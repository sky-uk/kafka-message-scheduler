package uk.sky.scheduler

import java.util.concurrent.TimeUnit

import cats.Monad
import cats.data.OptionT
import cats.effect.std.{MapRef, Queue}
import cats.effect.syntax.all.*
import cats.effect.{Async, Fiber}
import cats.syntax.all.*
import org.typelevel.log4cats.LoggerFactory

import scala.concurrent.duration.Duration

trait ScheduleQueue[F[_]] {
  def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit]
  def cancel(key: String): F[Unit]
}

object ScheduleQueue {
  type DeferredSchedule[F[_]] = Fiber[F, Throwable, Unit]

  def apply[F[_] : Async](
      scheduleRef: MapRef[F, String, Option[DeferredSchedule[F]]],
      queue: Queue[F, ScheduleEvent]
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

    }

}
