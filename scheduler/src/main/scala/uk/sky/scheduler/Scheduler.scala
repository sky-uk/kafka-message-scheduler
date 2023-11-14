package uk.sky.scheduler

import cats.effect.Concurrent
import fs2.Stream

class Scheduler[F[_] : Concurrent, O](
    eventSubscriber: EventSubscriber[F],
    scheduleQueue: ScheduleQueue[F],
    schedulePublisher: SchedulePublisher[F, O]
) {

  private val scheduleStream = eventSubscriber.messages.evalMapChunk { message =>
    message.value match {
      case Left(_) | Right(None) => if (message.expired) Concurrent[F].unit else scheduleQueue.cancel(message.key)
      case Right(Some(schedule)) => scheduleQueue.schedule(message.key, schedule)
    }
  }

  def stream: Stream[F, O] =
    scheduleStream.drain.merge(schedulePublisher.publish)
}
