package com.sky.kms

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import com.sksamuel.avro4s.{Decoder, Encoder, SchemaFor}
import org.apache.avro.Schema

package object avro {

  implicit val dateTimeToSchema: SchemaFor[OffsetDateTime] = _ =>
    Schema.create(Schema.Type.LONG)

  implicit val dateTimeEncoder: Encoder[OffsetDateTime] = (t: OffsetDateTime, _, _) =>
    Long.box(t.toInstant.toEpochMilli)

  implicit val dateTimeFDecoder: Decoder[OffsetDateTime] = (value: Any, _, _) =>
    Instant.ofEpochMilli(value.toString.toLong).atZone(ZoneOffset.UTC).toOffsetDateTime

}
