package uk.sky.scheduler

import java.util.concurrent.TimeUnit

import cats.Monad
import cats.data.OptionT
import cats.effect.std.{MapRef, Queue}
import cats.effect.syntax.all.*
import cats.effect.{Async, Fiber}
import cats.syntax.all.*
import org.typelevel.log4cats.LoggerFactory
import uk.sky.scheduler.domain.ScheduleEvent

import scala.concurrent.duration.Duration

trait ScheduleQueue[F[_]] {
  def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit]
  def cancel(key: String): F[Unit]
  def queue: Queue[F, ScheduleEvent]
}

object ScheduleQueue {
  type DeferredSchedule[F[_]] = Fiber[F, Throwable, Unit]

  def apply[F[_] : Async](
      scheduleRef: MapRef[F, String, Option[DeferredSchedule[F]]],
      eventQueue: Queue[F, ScheduleEvent]
  ): ScheduleQueue[F] = new ScheduleQueue[F] {
    override def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit] = {
      val deferred = for {
        now     <- Async[F].realTimeInstant
        delay    = Math.max(0, scheduleEvent.time - now.toEpochMilli)
        duration = Duration(delay, TimeUnit.MILLISECONDS)
        _       <- Async[F].delayBy(queue.offer(scheduleEvent), duration)
      } yield ()

      for {
        started <- deferred.start.guarantee(scheduleRef.unsetKey(key))
        _       <- scheduleRef.setKeyValue(key, started)
      } yield ()
    }

    override def cancel(key: String): F[Unit] = {
      for {
        started <- OptionT(scheduleRef.apply(key).get)
        _       <- OptionT.liftF(started.cancel)
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
          _ <- logger.info(s"Canceled JsonSchedule [$key]")
        } yield ()

      override def queue: Queue[F, ScheduleEvent] = delegate.queue
    }

  def live[F[_] : Async : LoggerFactory]: F[ScheduleQueue[F]] =
    for {
      scheduleRef <- MapRef.ofScalaConcurrentTrieMap[F, String, DeferredSchedule[F]]
      eventQueue  <- Queue.unbounded[F, ScheduleEvent]
    } yield ScheduleQueue.observed(ScheduleQueue(scheduleRef, eventQueue))

}
