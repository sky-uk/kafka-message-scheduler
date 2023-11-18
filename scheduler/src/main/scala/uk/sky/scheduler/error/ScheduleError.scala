package uk.sky.scheduler.error

import java.time.{Instant, OffsetDateTime, ZoneOffset}

enum ScheduleError(val message: String) extends Throwable(message) {
  case DecodeError(key: String, errorMessage: String)
      extends ScheduleError(s"Failed to decode [$key] with error $errorMessage")
  case InvalidTimeError(key: String, epoch: Long)
      extends ScheduleError(
        s"Time between now and ${OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC)} is not within 292 years for message [$key]"
      )
}
