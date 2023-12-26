package uk.sky.scheduler.stubs

import cats.Parallel
import cats.effect.std.{MapRef, Queue, Supervisor}
import cats.effect.syntax.all.*
import cats.effect.{Async, Deferred, Resource}
import cats.syntax.all.*
import uk.sky.scheduler.ScheduleQueue.CancelableSchedule
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.{Repository, ScheduleQueue}

object StubScheduleQueue {
  def apply[F[_] : Async : Parallel]: Resource[F, ScheduleQueue[F]] =
    for {
      allowEnqueue  <- Deferred[F, Unit].toResource
      repo          <- MapRef
                         .ofScalaConcurrentTrieMap[F, String, CancelableSchedule[F]]
                         .map(Repository[F, String, CancelableSchedule[F]](_))
                         .toResource
      scheduleQueue <- Queue.unbounded[F, ScheduleEvent].toResource
      supervisor    <- Supervisor[F]
    } yield ScheduleQueue(allowEnqueue, repo, scheduleQueue, supervisor)

}
