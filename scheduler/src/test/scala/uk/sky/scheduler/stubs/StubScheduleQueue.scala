package uk.sky.scheduler.stubs

import cats.Parallel
import cats.effect.std.Queue
import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Ref, Resource}
import cats.syntax.all.*
import fs2.Stream
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.{PriorityScheduleQueue, Repository, ScheduleQueue}

final class StubScheduleQueue[F[_] : Async](
    events: Queue[F, TestEvent],
    allowEnqueue: Deferred[F, Unit],
    repo: Repository[F, String, ScheduleEvent],
    priorityQueue: PriorityScheduleQueue[F],
    outputQueue: Queue[F, ScheduleEvent],
    wakeupRef: Ref[F, Deferred[F, Unit]]
) extends ScheduleQueue[F] {
  private val impl = ScheduleQueue(allowEnqueue, repo, priorityQueue, outputQueue, wakeupRef)

  override def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit] =
    impl.schedule(key, scheduleEvent) *> events.offer(TestEvent.Scheduled(scheduleEvent))

  override def cancel(key: String): F[Unit] =
    impl.cancel(key) *> events.offer(TestEvent.Canceled(key))

  override def schedules: Stream[F, ScheduleEvent] =
    impl.schedules
}

object StubScheduleQueue {
  def apply[F[_] : Async : Parallel](
      events: Queue[F, TestEvent],
      allowEnqueue: Deferred[F, Unit]
  )(using Meter[F]): Resource[F, StubScheduleQueue[F]] =
    for {
      repo          <- Repository.ofScalaConcurrentTrieMap[F, String, ScheduleEvent]("test").toResource
      priorityQueue <- PriorityScheduleQueue[F].toResource
      outputQueue   <- Queue.unbounded[F, ScheduleEvent].toResource
      initialWakeup <- Deferred[F, Unit].toResource
      wakeupRef     <- Ref.of[F, Deferred[F, Unit]](initialWakeup).toResource
      _             <- ScheduleQueue.schedulerFiber(allowEnqueue, repo, priorityQueue, outputQueue, wakeupRef).background
    } yield new StubScheduleQueue(events, allowEnqueue, repo, priorityQueue, outputQueue, wakeupRef)
}
