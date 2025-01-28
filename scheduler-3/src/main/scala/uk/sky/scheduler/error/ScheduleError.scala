package uk.sky.scheduler.error

import cats.syntax.all.*
import cats.{Eq, Show}
import org.apache.avro.Schema

enum ScheduleError(val message: String, val cause: Throwable) extends Throwable(message, cause) {
  case InvalidAvroError(schema: Schema, error: Throwable)
      extends ScheduleError(show"Avro message did not conform to Schema: ${schema.getFullName}: $schema", error)
}

object ScheduleError {
  given Eq[ScheduleError]   = Eq.fromUniversalEquals
  given Show[ScheduleError] = _.message

  private given Show[Schema] = _.toString()
}
