package uk.sky.scheduler.stubs

import fs2.Pipe
import uk.sky.scheduler.SchedulePublisher
import uk.sky.scheduler.domain.ScheduleEvent

object StubSchedulePublisher {
  def apply[F[_]]: SchedulePublisher[F, ScheduleEvent] =
    new SchedulePublisher[F, ScheduleEvent] {
      override def publish: Pipe[F, ScheduleEvent, ScheduleEvent] = identity
    }
}