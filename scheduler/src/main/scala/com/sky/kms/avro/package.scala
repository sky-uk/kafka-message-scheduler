package com.sky.kms

import com.sksamuel.avro4s.{Decoder, Encoder, SchemaFor}
import org.apache.avro.Schema

import java.time.{Instant, OffsetDateTime, ZoneOffset}

package object avro {

  implicit val dateTimeSchemaFor = SchemaFor[OffsetDateTime](Schema.create(Schema.Type.LONG))

  implicit object DateTimeEncoder extends Encoder[OffsetDateTime] {
    override def encode(value: OffsetDateTime): java.lang.Long = value.toInstant.toEpochMilli

    override def schemaFor: SchemaFor[OffsetDateTime] = SchemaFor[OffsetDateTime]
  }

  implicit object DateTimeDecoder extends Decoder[OffsetDateTime] {
    override def decode(value: Any): OffsetDateTime =
      Instant.ofEpochMilli(value.toString.toLong).atZone(ZoneOffset.UTC).toOffsetDateTime

    override def schemaFor: SchemaFor[OffsetDateTime] = SchemaFor[OffsetDateTime]
  }

}
