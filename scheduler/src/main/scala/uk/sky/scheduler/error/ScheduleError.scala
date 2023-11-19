package uk.sky.scheduler.error

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import org.apache.avro.Schema

enum ScheduleError(val message: String) extends Throwable(message) {
  case InvalidAvroError(schema: Schema)
      extends ScheduleError(s"Message did not conform to Schema: ${schema.getFullName}: $schema")
  case DecodeError(key: String, errorMessage: String)
      extends ScheduleError(s"Failed to decode [$key] with error $errorMessage")
  case InvalidTimeError(key: String, epoch: Long)
      extends ScheduleError(
        s"Time between now and ${OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC)} is not within 292 years for message [$key]"
      )
}
