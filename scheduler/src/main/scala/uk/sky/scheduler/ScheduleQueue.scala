package uk.sky.scheduler

import cats.Monad
import cats.data.OptionT
import cats.effect.std.{MapRef, Queue}
import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Fiber}
import cats.syntax.all.*
import org.typelevel.log4cats.LoggerFactory
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
      scheduleRef: MapRef[F, String, Option[CancelableSchedule[F]]],
      allowEnqueue: Deferred[F, Unit],
      eventQueue: Queue[F, ScheduleEvent]
  ): ScheduleQueue[F] = new ScheduleQueue[F] {
    override def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit] = {
      val delayScheduling = for {
        now  <- Async[F].realTimeInstant
        delay = Math.max(0, scheduleEvent.time - now.toEpochMilli)
        _    <- Async[F].delayBy(
                  allowEnqueue.get *> queue.offer(scheduleEvent) *> scheduleRef.unsetKey(key),
                  delay.milliseconds
                )
      } yield ()

      for {
        cancelableSchedule <- delayScheduling.start
        _                  <- scheduleRef.setKeyValue(key, cancelableSchedule)
      } yield ()
    }

    override def cancel(key: String): F[Unit] = {
      for {
        started <- OptionT(scheduleRef.apply(key).get)
        _       <- OptionT.liftF(started.cancel)
        _       <- OptionT.liftF(scheduleRef.unsetKey(key))
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

  def live[F[_] : Async : LoggerFactory](allowEnqueue: Deferred[F, Unit]): F[ScheduleQueue[F]] =
    for {
      scheduleRef <- MapRef.ofScalaConcurrentTrieMap[F, String, CancelableSchedule[F]]
      eventQueue  <- Queue.unbounded[F, ScheduleEvent]
    } yield ScheduleQueue.observed(ScheduleQueue(scheduleRef, allowEnqueue, eventQueue))

}
