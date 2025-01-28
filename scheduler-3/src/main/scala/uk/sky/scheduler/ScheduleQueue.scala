package uk.sky.scheduler

import fs2.Stream
import uk.sky.scheduler.domain.ScheduleEvent

trait ScheduleQueue[F[_]] {
  def schedule(key: String, scheduleEvent: ScheduleEvent): F[Unit]
  def cancel(key: String): F[Unit]
  def schedules: Stream[F, ScheduleEvent]
}
