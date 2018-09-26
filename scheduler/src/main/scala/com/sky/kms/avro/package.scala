package com.sky.kms

import java.time.format.DateTimeFormatter
import java.time.{Instant, OffsetDateTime, ZoneOffset}

import com.sksamuel.avro4s.{FromValue, ToSchema, ToValue}
import org.apache.avro.Schema
import org.apache.avro.Schema.Field

package object avro {

  private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

  implicit val offsetDateTimeToSchema = new ToSchema[OffsetDateTime] {
    override val schema: Schema = Schema.create(Schema.Type.LONG)
  }

  implicit val offsetDateTimeFromValue = new FromValue[OffsetDateTime] {
    override def apply(value: Any, field: Field): OffsetDateTime = Instant.ofEpochMilli(value.toString.toLong).atZone(ZoneOffset.UTC).toOffsetDateTime
  }

  implicit val dateTimeToValue: ToValue[OffsetDateTime] = new ToValue[OffsetDateTime] {
    override def apply(time: OffsetDateTime): Long = time.toInstant.toEpochMilli
  }
}
