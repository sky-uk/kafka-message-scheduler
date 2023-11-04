package uk.sky.scheduler

import cats.effect.Concurrent
import fs2.Stream

class Scheduler[F[_] : Concurrent, O](
    eventSubscriber: EventSubscriber[F],
    scheduleQueue: ScheduleQueue[F],
    schedulePublisher: SchedulePublisher[F, O]
) {

  private val scheduleStream = eventSubscriber.messages.evalMap { message =>
    message.value match {
      case Event.Update(key, schedule) => scheduleQueue.schedule(key, schedule)
      case Event.Delete(key)           => scheduleQueue.cancel(key)
      case Event.Error(key, _)         => scheduleQueue.cancel(key)
    }
  }

  def stream: Stream[F, O] =
    scheduleStream.drain.merge(schedulePublisher.publish)
}
