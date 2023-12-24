package uk.sky.scheduler.error

import org.apache.avro.Schema

enum ScheduleError(val message: String) extends Throwable(message) {
  case InvalidAvroError(schema: Schema, error: String)
      extends ScheduleError(s"Avro message did not conform to Schema: ${schema.getFullName}: $schema with error $error")

  case NotJsonError(payload: String, error: String)
      extends ScheduleError(s"'$payload' was not valid JSON with error: $error")

  case InvalidJsonError(payload: String, error: String)
      extends ScheduleError(s"JSON '$payload' did not conform to Schema with error: $error")

  case DecodeError(key: String, error: String) extends ScheduleError(s"Failed to decode [$key] with error: $error")
}
