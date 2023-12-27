package uk.sky.scheduler.stubs

import cats.Parallel
import cats.effect.std.Queue
import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Resource}
import cats.syntax.all.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.stubs.StubScheduleQueue.Status
import uk.sky.scheduler.util.testSyntax.*
import uk.sky.scheduler.{EventSubscriber, Message, SchedulePublisher, ScheduleQueue, Scheduler}

final class StubScheduler[F[_] : Async : Parallel](
    events: Queue[F, (String, Status)],
    input: Queue[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]],
    eventSubscriber: EventSubscriber[F],
    scheduleQueue: ScheduleQueue[F],
    schedulePublisher: SchedulePublisher[F, ScheduleEvent]
) extends Scheduler[F, ScheduleEvent](eventSubscriber, scheduleQueue, schedulePublisher) {
  def produce(
      messages: Message[Either[ScheduleError, Option[ScheduleEvent]]]*
  ): F[Unit] =
    messages
      .traverse(input.offer)
      .void

  def runStreamAndTake(n: Int): F[List[ScheduleEvent]] =
    stream
      .take(n)
      .compile
      .toList
      .testTimeout()

  def runStreamInBackground: F[Unit] =
    stream.compile.drain.start.void

  def takeEvent: F[(String, Status)] =
    events.take
      .testTimeout()
}

object StubScheduler {
  def apply[F[_] : Async : Parallel]: Resource[F, StubScheduler[F]] =
    for {
      events         <- Queue.unbounded[F, (String, Status)].toResource
      allowEnqueue   <- Deferred[F, Unit].flatTap(_.complete(())).toResource
      stubSubscriber <- StubEventSubscriber[F].toResource
      stubQueue      <- StubScheduleQueue[F](events, allowEnqueue)
      stubPublisher   = StubSchedulePublisher[F]
    } yield new StubScheduler(
      events = events,
      input = stubSubscriber.input,
      eventSubscriber = stubSubscriber,
      scheduleQueue = stubQueue,
      schedulePublisher = stubPublisher
    )
}
