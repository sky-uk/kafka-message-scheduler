package uk.sky.scheduler

import cats.Monad
import cats.data.OptionT
import cats.effect.std.Queue
import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Fiber}
import cats.syntax.all.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.domain.ScheduleEvent

import scala.concurrent.duration.*

trait ScheduleQueue[F[_]] {
  def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit]
  def cancel(key: String): F[Unit]
  def queue: Queue[F, ScheduleEvent]
}

object ScheduleQueue {
  type CancelableSchedule[F[_]] = Fiber[F, Throwable, Unit]

  def apply[F[_] : Async](
      repository: Repository[F, String, CancelableSchedule[F]],
      allowEnqueue: Deferred[F, Unit],
      eventQueue: Queue[F, ScheduleEvent]
  ): ScheduleQueue[F] = new ScheduleQueue[F] {
    override def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit] = {
      val delayScheduling = for {
        now  <- Async[F].realTimeInstant
        delay = Math.max(0, scheduleEvent.schedule.time - now.toEpochMilli)
        _    <- Async[F].delayBy(
                  allowEnqueue.get *> queue.offer(scheduleEvent) *> repository.delete(key),
                  delay.milliseconds
                )
      } yield ()

      for {
        previous           <- repository.get(key)
        _                  <- previous.fold(Async[F].unit)(_.cancel)
        cancelableSchedule <- delayScheduling.start
        _                  <- repository.set(key, cancelableSchedule)
      } yield ()
    }

    override def cancel(key: String): F[Unit] = {
      for {
        started <- OptionT(repository.get(key))
        _       <- OptionT.liftF(started.cancel)
        _       <- OptionT.liftF(repository.delete(key))
      } yield ()
    }.value.void

    override def queue: Queue[F, ScheduleEvent] = eventQueue
  }

  def observed[F[_] : Monad : LoggerFactory](delegate: ScheduleQueue[F]): ScheduleQueue[F] =
    new ScheduleQueue[F] {
      val logger = LoggerFactory[F].getLogger

      override def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit] =
        for {
          _ <- delegate.schedule(key, scheduleEvent)
          _ <- logger.info(s"Scheduled [$key]")
        } yield ()

      override def cancel(key: String): F[Unit] =
        for {
          _ <- delegate.cancel(key)
          _ <- logger.info(s"Canceled Schedule [$key]")
        } yield ()

      override def queue: Queue[F, ScheduleEvent] = delegate.queue
    }

  def live[F[_] : Async : LoggerFactory : Meter](allowEnqueue: Deferred[F, Unit]): F[ScheduleQueue[F]] =
    for {
      repo       <- Repository.live[F, String, CancelableSchedule[F]]("schedules")
      eventQueue <- Queue.unbounded[F, ScheduleEvent]
    } yield ScheduleQueue.observed(ScheduleQueue(repo, allowEnqueue, eventQueue))

}
