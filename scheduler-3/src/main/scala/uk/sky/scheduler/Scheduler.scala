package uk.sky.scheduler

import cats.Parallel
import cats.data.Reader
import cats.effect.*
import cats.effect.syntax.all.*
import cats.effect.{Async, Concurrent, Deferred, Resource}
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.config.Config
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.message.Message
import uk.sky.scheduler.message.Metadata.*

class Scheduler[F[_] : Concurrent, O](
    eventSubscriber: EventSubscriber[F],
    scheduleQueue: ScheduleQueue[F],
    schedulePublisher: SchedulePublisher[F, O]
) {
  private val scheduleEvents = eventSubscriber.messages.evalTapChunk { case Message(key, source, value, metadata) =>
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
  def live[F[_] : Async : Parallel : LoggerFactory : Meter]: Reader[Config, Resource[F, Scheduler[F, Unit]]] = Reader {
    config =>
      for {
        allowEnqueue     <- Deferred[F, Unit].toResource
        eventSubscriber  <- EventSubscriber.live[F](config.kafka, allowEnqueue).toResource
        scheduleQueue    <- Resource.pure(??? : ScheduleQueue[F])
        schedulePublisher = SchedulePublisher.live[F](config.kafka)
      } yield Scheduler(eventSubscriber, scheduleQueue, schedulePublisher)
  }
}
