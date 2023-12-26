package uk.sky.scheduler.stubs

import cats.Parallel
import cats.effect.std.Queue
import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Resource}
import cats.syntax.all.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.util.testSyntax.*
import uk.sky.scheduler.{EventSubscriber, Message, SchedulePublisher, ScheduleQueue, Scheduler}

final class StubScheduler[F[_] : Async : Parallel](
    allowEnqueue: Deferred[F, Unit],
    eventQueue: Queue[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]],
    eventSubscriber: EventSubscriber[F],
    scheduleQueue: ScheduleQueue[F],
    schedulePublisher: SchedulePublisher[F, ScheduleEvent]
) extends Scheduler[F, ScheduleEvent](eventSubscriber, scheduleQueue, schedulePublisher) {
  val signalLoaded: F[Unit] = allowEnqueue.complete(()).void

  def produce(
      messages: Message[Either[ScheduleError, Option[ScheduleEvent]]]*
  ): F[Unit] =
    messages
      .traverse(eventQueue.offer)
      .void

  def consume(n: Int): F[List[ScheduleEvent]] =
    stream
      .take(n)
      .compile
      .toList
      .testTimeout()
}

object StubScheduler {
  def apply[F[_] : Async : Parallel]: Resource[F, StubScheduler[F]] =
    for {
      allowEnqueue   <- Deferred[F, Unit].toResource
      stubSubscriber <- StubEventSubscriber[F].toResource
      stubQueue      <- StubScheduleQueue[F](allowEnqueue)
      stubPublisher   = StubSchedulePublisher[F]
    } yield new StubScheduler(
      allowEnqueue = allowEnqueue,
      eventQueue = stubSubscriber.eventQueue,
      eventSubscriber = stubSubscriber,
      scheduleQueue = stubQueue,
      schedulePublisher = stubPublisher
    )
}
