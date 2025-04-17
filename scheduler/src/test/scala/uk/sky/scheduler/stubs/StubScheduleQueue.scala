package uk.sky.scheduler.stubs

import cats.Parallel
import cats.effect.std.{MapRef, Queue, Supervisor}
import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Resource}
import cats.syntax.all.*
import fs2.Stream
import uk.sky.scheduler.ScheduleQueue.CancelableSchedule
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.{Repository, ScheduleQueue}

final class StubScheduleQueue[F[_] : Async : Parallel](
    events: Queue[F, TestEvent],
    allowEnqueue: Deferred[F, Unit],
    repo: Repository[F, String, CancelableSchedule[F]],
    scheduleQueue: Queue[F, ScheduleEvent],
    supervisor: Supervisor[F]
) extends ScheduleQueue[F] {
  private val impl = ScheduleQueue(allowEnqueue, repo, scheduleQueue, supervisor)

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
  ): Resource[F, StubScheduleQueue[F]] =
    for {
      repo          <- MapRef
                         .ofScalaConcurrentTrieMap[F, String, CancelableSchedule[F]]
                         .map(Repository[F, String, CancelableSchedule[F]](_))
                         .toResource
      scheduleQueue <- Queue.unbounded[F, ScheduleEvent].toResource
      supervisor    <- Supervisor[F]
    } yield new StubScheduleQueue(events, allowEnqueue, repo, scheduleQueue, supervisor)
}
