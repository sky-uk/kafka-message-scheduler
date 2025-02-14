package uk.sky.scheduler

import fs2.Pipe
import uk.sky.scheduler.domain.ScheduleEvent

trait SchedulePublisher[F[_], O] {
  def publish: Pipe[F, ScheduleEvent, O]
}