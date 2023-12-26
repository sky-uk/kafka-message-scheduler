package uk.sky.scheduler.stubs

import cats.Parallel
import cats.effect.std.Queue
import cats.effect.syntax.all.*
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.{EventSubscriber, Message, SchedulePublisher, ScheduleQueue, Scheduler}

final class StubScheduler[F[_] : Async : Parallel](
    eventQueue: Queue[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]],
    eventSubscriber: EventSubscriber[F],
    scheduleQueue: ScheduleQueue[F],
    schedulePublisher: SchedulePublisher[F, ScheduleEvent]
) extends Scheduler[F, ScheduleEvent](eventSubscriber, scheduleQueue, schedulePublisher) {
  def produce(
      messages: Message[Either[ScheduleError, Option[ScheduleEvent]]]*
  ): F[Unit] = messages.traverse(eventQueue.offer).void
}

object StubScheduler {
  def apply[F[_] : Async : Parallel]: Resource[F, StubScheduler[F]] =
    for {
      stubSubscriber <- StubEventSubscriber[F].toResource
      stubQueue      <- StubScheduleQueue[F]
      stubPublisher   = StubSchedulePublisher[F]
    } yield new StubScheduler(
      eventQueue = stubSubscriber.eventQueue,
      eventSubscriber = stubSubscriber,
      scheduleQueue = stubQueue,
      schedulePublisher = stubPublisher
    )
}
