package uk.sky.scheduler.kafka

import fs2.kafka.CommittableOffset
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError

enum Event[F[_]] {
  def key: String
  def offset: CommittableOffset[F]

  case Update(key: String, schedule: ScheduleEvent, offset: CommittableOffset[F])
  case Delete(key: String, offset: CommittableOffset[F])
  case Error(key: String, error: ScheduleError, offset: CommittableOffset[F])
}
