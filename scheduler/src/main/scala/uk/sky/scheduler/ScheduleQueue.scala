package uk.sky.scheduler

import cats.effect.std.{Queue, Supervisor}
import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Fiber}
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
  type CancelableSchedule[F[_]] = Fiber[F, Throwable, Unit]

  def apply[F[_] : Async](
      allowEnqueue: Deferred[F, Unit],
      repository: Repository[F, String, CancelableSchedule[F]],
      queue: Queue[F, ScheduleEvent],
      supervisor: Supervisor[F]
  ): ScheduleQueue[F] = new ScheduleQueue[F] {

    /** Delay offering a schedule to the queue by [[delay]]. This has 3 guarantees:
      *
      *   - It will not offer a schedule until [[allowEnqueue]] has been completed. This prevents schedules being
      *     prematurely fired on startup, if there are pending updates yet to be read.
      *   - It will not delete a schedule from the repository until [[storeLock]] has been completed. This prevents a
      *     race condition between storing a schedule and deleting it - for example, if a schedule is to be fired
      *     immediately the fiber will run `delete` on the repository <b>then</b> store the canceled fiber.
      *   - If offering a schedule to the queue fails, we guarantee that it will be removed from the repository.
      *
      * Note that offering to the underlying queue is uncancelable. This means that a schedule can only be canceled
      * while in the waiting state and not after it is submitted for scheduling.
      */
    private def delayScheduling(
        key: String,
        scheduleEvent: ScheduleEvent,
        delay: FiniteDuration,
        storeLock: Deferred[F, Unit]
    ): F[Unit] =
      Async[F]
        .delayBy(
          for {
            _ <- allowEnqueue.get
            _ <- queue.offer(scheduleEvent).uncancelable.guarantee(storeLock.get >> repository.delete(key))
          } yield (),
          time = delay
        )

    override def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit] =
      for {
        previous   <- repository.get(key)
        _          <- previous.fold(Async[F].unit)(_.cancel)
        now        <- Async[F].epochMilli
        delay      <- Either
                        .catchNonFatal(Math.max(0, scheduleEvent.schedule.time - now).milliseconds)
                        .getOrElse(Long.MaxValue.nanos)
                        .pure
        storeLock  <- Deferred[F, Unit]
        cancelable <- supervisor.supervise(delayScheduling(key, scheduleEvent, delay, storeLock))
        _          <- repository.set(key, cancelable)
        _          <- storeLock.complete(())
      } yield ()

    override def cancel(key: String): F[Unit] =
      repository.get(key).flatMap {
        case Some(started) => started.cancel.guarantee(repository.delete(key))
        case None          => Async[F].unit
      }

    override def schedules: Stream[F, ScheduleEvent] =
      Stream.fromQueueUnterminated(queue)
  }

  def observed[F[_] : Monad : LoggerFactory](delegate: ScheduleQueue[F]): ScheduleQueue[F] = {
    val logger = LoggerFactory[F].getLogger

    new ScheduleQueue[F] {
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
  }

  def live[F[_] : Async : Parallel : LoggerFactory : Meter](
      allowEnqueue: Deferred[F, Unit],
      supervisor: Supervisor[F]
  ): F[ScheduleQueue[F]] =
    for {
      repo         <- Repository.ofConcurrentHashMap[F, String, CancelableSchedule[F]]("schedules")
      eventQueue   <- Queue.unbounded[F, ScheduleEvent]
      scheduleQueue = ScheduleQueue(allowEnqueue, repo, eventQueue, supervisor)
      observed      = ScheduleQueue.observed(scheduleQueue)
    } yield observed

}
