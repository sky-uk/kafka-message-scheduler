package com.sky.kms

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import com.sksamuel.avro4s.{Decoder, Encoder, SchemaFor}
import org.apache.avro.Schema

package object avro {

  implicit object DateTimeSchemaFor extends SchemaFor[OffsetDateTime] {
    override val schema: Schema = Schema.create(Schema.Type.LONG)
  }

  implicit object DateTimeEncoder extends Encoder[OffsetDateTime] {
    override def encode(value: OffsetDateTime, schema: Schema): java.lang.Long = value.toInstant.toEpochMilli
  }

  implicit object DateTimeDecoder extends Decoder[OffsetDateTime] {
    override def decode(value: Any, schema: Schema): OffsetDateTime =
      Instant.ofEpochMilli(value.toString.toLong).atZone(ZoneOffset.UTC).toOffsetDateTime
  }
}
