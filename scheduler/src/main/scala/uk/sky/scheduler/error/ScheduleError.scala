package uk.sky.scheduler.error

import cats.{Eq, Show}
import org.apache.avro.Schema

enum ScheduleError(val message: String, val cause: Throwable) extends Throwable(message, cause) {
  case InvalidAvroError(schema: Schema, error: Throwable)
      extends ScheduleError(s"Avro message did not conform to Schema: ${schema.getFullName}: $schema", error)

  case NotJsonError(payload: String, error: Throwable) extends ScheduleError(s"'$payload' was not valid JSON", error)

  case InvalidJsonError(payload: String, error: Throwable)
      extends ScheduleError(s"JSON '$payload' did not conform to Schema", error)

  case DecodeError(key: String, error: Throwable) extends ScheduleError(s"Failed to decode [$key]", error)
}

object ScheduleError {
  given Eq[ScheduleError]   = Eq.fromUniversalEquals
  given Show[ScheduleError] = _.message
}
