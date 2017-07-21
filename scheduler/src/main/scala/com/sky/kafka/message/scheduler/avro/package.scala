package com.sky.kafka.message.scheduler

import java.time.OffsetDateTime

import com.sksamuel.avro4s.{FromValue, ToSchema, ToValue}
import org.apache.avro.Schema
import org.apache.avro.Schema.Field

package object avro {

  implicit val offsetDateTimeToSchema = new ToSchema[OffsetDateTime] {
    override val schema: Schema = Schema.create(Schema.Type.STRING)
  }

  implicit val offsetDateTimeFromValue = new FromValue[OffsetDateTime] {
    override def apply(value: Any, field: Field): OffsetDateTime = OffsetDateTime.parse(value.toString)
  }

  implicit val offsetDateTimeToValue = new ToValue[OffsetDateTime] {
    override def apply(value: OffsetDateTime): String = value.toString
  }
}
