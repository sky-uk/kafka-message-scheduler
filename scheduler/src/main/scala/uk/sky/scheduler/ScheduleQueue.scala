package uk.sky.scheduler

import cats.effect.kernel.Resource
import cats.effect.std.{Queue, Supervisor}
import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Fiber}
import cats.syntax.all.*
import cats.{Monad, Parallel}
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.domain.ScheduleEvent

import scala.concurrent.duration.*

trait ScheduleQueue[F[_]] {
  def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit]
  def cancel(key: String): F[Unit]
  def schedules: Stream[F, ScheduleEvent]
}

object ScheduleQueue {
  type CancelableSchedule[F[_]] = Fiber[F, Throwable, Unit]

  def apply[F[_] : Async : Parallel](
      repository: Repository[F, String, CancelableSchedule[F]],
      allowEnqueue: Deferred[F, Unit],
      queue: Queue[F, ScheduleEvent],
      supervisor: Supervisor[F]
  ): ScheduleQueue[F] = new ScheduleQueue[F] {
    override def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit] = {
      def delayScheduling(delay: FiniteDuration): F[CancelableSchedule[F]] =
        for {
          cancelable <- supervisor.supervise(
                          Async[F]
                            .delayBy(
                              for {
                                _ <- allowEnqueue.get                                     // Block until Queuing is allowed
                                _ <- queue.offer(scheduleEvent) &> repository.delete(key) // Offer & Delete in Parallel
                              } yield (),
                              time = delay
                            )
                        )
        } yield cancelable

      for {
        previous   <- repository.get(key)
        _          <- previous.fold(Async[F].unit)(_.cancel) // Cancel the previous Schedule if it exists
        now        <- Async[F].realTimeInstant
        delay       = Either
                        .catchNonFatal(Math.max(0, scheduleEvent.schedule.time - now.toEpochMilli).milliseconds)
                        .getOrElse(Long.MaxValue.nanos)
        cancelable <- delayScheduling(delay)
        _          <- repository.set(key, cancelable)
      } yield ()
    }

    override def cancel(key: String): F[Unit] =
      repository.get(key).flatMap {
        case Some(started) => started.cancel &> repository.delete(key) // Cancel & Delete in Parallel
        case None          => Async[F].unit
      }

    override def schedules: Stream[F, ScheduleEvent] =
      Stream.fromQueueUnterminated(queue)
  }

  def observed[F[_] : Monad : LoggerFactory](delegate: ScheduleQueue[F]): ScheduleQueue[F] =
    new ScheduleQueue[F] {
      val logger = LoggerFactory[F].getLogger

      override def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit] =
        for {
          result <- delegate.schedule(key, scheduleEvent)
          _      <- logger.info(s"Scheduled [$key]")
        } yield result

      override def cancel(key: String): F[Unit] =
        for {
          _ <- delegate.cancel(key)
          _ <- logger.info(s"Canceled Schedule [$key]")
        } yield ()

      override def schedules: Stream[F, ScheduleEvent] =
        delegate.schedules.evalTapChunk { scheduleEvent =>
          logger.debug(s"ScheduleEvent [${scheduleEvent.metadata.id}] received in Stream")
        }
    }

  def live[F[_] : Async : Parallel : LoggerFactory : Meter](
      allowEnqueue: Deferred[F, Unit]
  ): Resource[F, ScheduleQueue[F]] =
    for {
      repo       <- Repository.live[F, String, CancelableSchedule[F]]("schedules").toResource
      supervisor <- Supervisor[F]
      eventQueue <- Queue.unbounded[F, ScheduleEvent].toResource
    } yield ScheduleQueue.observed(ScheduleQueue(repo, allowEnqueue, eventQueue, supervisor))

}
