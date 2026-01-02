package uk.sky.scheduler

import cats.Parallel
import cats.effect.*
import cats.effect.std.Supervisor
import cats.effect.syntax.all.*
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.config.Config
import uk.sky.scheduler.core.ResourceReader
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.message.Message
import uk.sky.scheduler.message.Metadata.*

class Scheduler[F[_] : Concurrent, O](
    eventSubscriber: EventSubscriber[F],
    scheduleQueue: ScheduleQueue[F],
    schedulePublisher: SchedulePublisher[F, O]
) {
  private val scheduleEvents = eventSubscriber.messages.evalTapChunk { case Message(key, _, value, metadata) =>
    value match {
      case Left(_)               => scheduleQueue.cancel(key)
      case Right(None)           => Concurrent[F].unlessA(metadata.isExpired)(scheduleQueue.cancel(key))
      case Right(Some(schedule)) => scheduleQueue.schedule(key, schedule)
    }
  }

  def stream: Stream[F, O] =
    scheduleEvents.drain
      .merge(scheduleQueue.schedules.through(schedulePublisher.publish))
}

object Scheduler {
  def live[F[_] : Async : Parallel : LoggerFactory : Meter](
      supervisor: Supervisor[F]
  ): ResourceReader[F, Config, Scheduler[F, Unit]] =
    for {
      schedulePublisher <- SchedulePublisher.live[F].lift.mapF(_.toResource)
      allowEnqueue      <- ResourceReader.liftF(Deferred[F, Unit]).mapF(_.toResource)
      scheduleQueue     <- ResourceReader.liftF(ScheduleQueue.live(allowEnqueue))
      subscriber        <- ResourceReader(EventSubscriber.live[F](_, allowEnqueue)).mapF(_.toResource)
    } yield new Scheduler(subscriber, scheduleQueue, schedulePublisher)
}
