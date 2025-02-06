package uk.sky.scheduler

import cats.data.Reader
import cats.effect.*
import fs2.Stream
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
  def live[F[_] : Concurrent]: Reader[Config, Resource[F, Scheduler[F, Unit]]] = Reader { config =>
    for {
      eventSubscriber   <- Resource.pure(??? : EventSubscriber[F])
      scheduleQueue     <- Resource.pure(??? : ScheduleQueue[F])
      schedulePublisher <- Resource.pure(??? : SchedulePublisher[F, Unit])
    } yield Scheduler(eventSubscriber, scheduleQueue, schedulePublisher)
  }

}
