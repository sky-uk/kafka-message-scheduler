package uk.sky.scheduler.error

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import org.apache.avro.Schema

enum ScheduleError(val message: String) extends Throwable(message) {
  case InvalidAvroError(schema: Schema, error: String)
      extends ScheduleError(s"Avro message did not conform to Schema: ${schema.getFullName}: $schema with error $error")

  case NotJsonError(payload: String, error: String)
      extends ScheduleError(s"'$payload' was not valid JSON with error: $error")

  case InvalidJsonError(payload: String, error: String)
      extends ScheduleError(s"JSON '$payload' did not conform to Schema with error: $error")

  case DecodeError(key: String, error: String) extends ScheduleError(s"Failed to decode [$key] with error: $error")

  case InvalidTimeError(key: String, epoch: Long)
      extends ScheduleError(
        s"Time between now and ${OffsetDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZoneOffset.UTC)} is not within 292 years for message [$key]"
      )
}
