package uk.sky.scheduler.kafka.avro

import cats.effect.Sync
import cats.syntax.all.*
import fs2.kafka.{Deserializer, Serializer, ValueDeserializer, ValueSerializer}
import vulcan.Codec

given avroScheduleCodec: Codec[AvroSchedule] = Codec.record[AvroSchedule](
  name = "Schedule",
  namespace = "com.sky.kms.domain"
) { field =>
  (
    field("time", _.time),
    field("topic", _.topic),
    field("key", _.key),
    field("value", _.value),
    field("headers", _.headers)
  ).mapN(AvroSchedule.apply)
}

def avroBinaryDeserializer[F[_] : Sync, V : Codec]: ValueDeserializer[F, V] =
  Codec[V].schema.fold(
    e => Deserializer.fail(e.throwable),
    schema =>
      Deserializer
        .lift[F, V] { bytes =>
          Sync[F]
            .fromEither(Codec.fromBinary[V](bytes, schema).leftMap(_.throwable))
        }
  )

def avroBinarySerializer[F[_] : Sync, V : Codec]: ValueSerializer[F, V] =
  Serializer.lift[F, V](v => Sync[F].fromEither(Codec.toBinary[V](v).leftMap(_.throwable)))
