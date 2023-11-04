package uk.sky.scheduler

import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError

enum Event {
  def key: String

  case Update(key: String, schedule: ScheduleEvent)
  case Delete(key: String)
  case Error(key: String, error: ScheduleError)
}
