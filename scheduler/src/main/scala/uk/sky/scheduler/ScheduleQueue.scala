package uk.sky.scheduler

import cats.effect.std.Queue
import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Resource}
import cats.syntax.all.*
import cats.{Monad, Parallel}
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.syntax.all.*

import scala.concurrent.duration.*

trait ScheduleQueue[F[_]] {
  def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit]
  def cancel(key: String): F[Unit]
  def schedules: Stream[F, ScheduleEvent]
}

object ScheduleQueue {

  def apply[F[_] : Async](
      allowEnqueue: Deferred[F, Unit],
      repository: Repository[F, String, ScheduleEvent],
      priorityQueue: PriorityScheduleQueue[F],
      outputQueue: Queue[F, ScheduleEvent],
      notifier: Notifier[F]
  ): ScheduleQueue[F] = new ScheduleQueue[F] {

    override def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit] =
      for {
        _ <- repository.set(key, scheduleEvent)
        _ <- priorityQueue.enqueue(key, scheduleEvent)
        _ <- notifier.signal
      } yield ()

    override def cancel(key: String): F[Unit] =
      for {
        _ <- repository.delete(key)
        _ <- priorityQueue.remove(key)
        _ <- notifier.signal
      } yield ()

    override def schedules: Stream[F, ScheduleEvent] =
      Stream.fromQueueUnterminated(outputQueue)
  }

  def observed[F[_] : Monad : LoggerFactory](delegate: ScheduleQueue[F]): F[ScheduleQueue[F]] =
    for {
      logger <- LoggerFactory[F].create
    } yield new ScheduleQueue[F] {
      override def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit] =
        for {
          result <- delegate.schedule(key, scheduleEvent)
          _      <- logger.info(show"Scheduled [$key]")
        } yield result

      override def cancel(key: String): F[Unit] =
        for {
          _ <- delegate.cancel(key)
          _ <- logger.info(show"Canceled Schedule [$key]")
        } yield ()

      override def schedules: Stream[F, ScheduleEvent] =
        delegate.schedules.evalTapChunk { scheduleEvent =>
          logger.info(
            show"Scheduled [${scheduleEvent.metadata.id}] to ${scheduleEvent.schedule.topic} due at ${scheduleEvent.schedule.time}"
          )
        }
    }

  def resource[F[_] : Async : Parallel : LoggerFactory : Meter](
      allowEnqueue: Deferred[F, Unit]
  ): Resource[F, ScheduleQueue[F]] =
    for {
      repo          <- Resource.eval(Repository.ofScalaConcurrentTrieMap[F, String, ScheduleEvent]("schedules"))
      priorityQueue <- Resource.eval(PriorityScheduleQueue[F])
      outputQueue   <- Resource.eval(Queue.unbounded[F, ScheduleEvent])
      notifier      <- Resource.eval(Notifier[F])
      scheduleQueue  = ScheduleQueue(allowEnqueue, repo, priorityQueue, outputQueue, notifier)
      _             <- schedulerFiber(allowEnqueue, repo, priorityQueue, outputQueue, notifier).background
      observed      <- Resource.eval(ScheduleQueue.observed(scheduleQueue))
    } yield observed

  def live[F[_] : Async : Parallel : LoggerFactory : Meter](
      allowEnqueue: Deferred[F, Unit]
  ): Resource[F, ScheduleQueue[F]] =
    resource(allowEnqueue)

  private[scheduler] def schedulerFiber[F[_] : Async](
      allowEnqueue: Deferred[F, Unit],
      repository: Repository[F, String, ScheduleEvent],
      priorityQueue: PriorityScheduleQueue[F],
      outputQueue: Queue[F, ScheduleEvent],
      notifier: Notifier[F]
  ): F[Unit] = {

    def processNext: F[Unit] =
      priorityQueue.peek.flatMap {
        case None =>
          notifier.await

        case Some((key, scheduleEvent)) =>
          for {
            now        <- Async[F].epochMilli
            delayMillis = Math.max(0, scheduleEvent.schedule.time - now)
            duration    = Either
                            .catchNonFatal(delayMillis.milliseconds)
                            .getOrElse(Long.MaxValue.nanos)
            _          <- if (delayMillis > 0) {
                            for {
                              _ <- notifier.await.race(Async[F].sleep(duration)).void
                              _ <- notifier.refresh
                            } yield ()
                          } else fireSchedule(key, scheduleEvent)
          } yield ()
      }

    def fireSchedule(key: String, expectedSchedule: ScheduleEvent): F[Unit] =
      repository.get(key).flatMap {
        case Some(current) if current.schedule.time == expectedSchedule.schedule.time =>
          for {
            _ <- outputQueue.offer(current)
            _ <- repository.delete(key)
            _ <- priorityQueue.dequeue
          } yield ()

        case Some(current) =>
          for {
            _ <- priorityQueue.dequeue
            _ <- priorityQueue.enqueue(key, current)
          } yield ()

        case None =>
          priorityQueue.dequeue.void
      }

    def loop: F[Unit] =
      processNext >> loop

    allowEnqueue.get >> loop
  }

}
