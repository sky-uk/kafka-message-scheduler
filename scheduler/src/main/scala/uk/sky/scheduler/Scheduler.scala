package uk.sky.scheduler

import cats.Parallel
import cats.data.Reader
import cats.effect.std.Queue
import cats.effect.syntax.all.*
import cats.effect.{Async, Concurrent, Deferred, Resource}
import cats.syntax.all.*
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.config.Config
import uk.sky.scheduler.domain.ScheduleEvent

class Scheduler[F[_] : Concurrent, O](
    eventSubscriber: EventSubscriber[F],
    scheduleQueue: ScheduleQueue[F],
    schedulePublisher: SchedulePublisher[F, O]
) {
  private val scheduleStream = eventSubscriber.messages.evalMapChunk { message =>
    message.value match {
      case Left(_)               => scheduleQueue.cancel(message.key)
      case Right(None)           => if (message.expired) Concurrent[F].unit else scheduleQueue.cancel(message.key)
      case Right(Some(schedule)) => scheduleQueue.schedule(message.key, schedule).void
    }
  }

  def stream: Stream[F, O] =
    scheduleStream.drain.merge(schedulePublisher.publish)
}

object Scheduler {
  def live[F[_] : Async : Parallel : LoggerFactory : Meter]: Reader[Config, Resource[F, Scheduler[F, Unit]]] =
    Reader { config =>
      for {
        eventQueue       <- Queue.unbounded[F, ScheduleEvent].toResource
        allowEnqueue     <- Deferred[F, Unit].toResource
        scheduleQueue    <- ScheduleQueue.live[F](eventQueue, allowEnqueue).toResource
        eventSubscriber  <- EventSubscriber.live[F](config.scheduler, allowEnqueue).toResource
        schedulePublisher = SchedulePublisher.kafka[F](config.scheduler, eventQueue)
      } yield Scheduler[F, Unit](eventSubscriber, scheduleQueue, schedulePublisher)
    }
}
