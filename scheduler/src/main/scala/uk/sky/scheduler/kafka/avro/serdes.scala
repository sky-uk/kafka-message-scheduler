package uk.sky.scheduler.kafka.avro

import cats.effect.Sync
import cats.syntax.all.*
import fs2.kafka.{Deserializer, Serializer, ValueDeserializer, ValueSerializer}
import uk.sky.scheduler.error.ScheduleError
import vulcan.Codec

given avroScheduleCodec: Codec[AvroSchedule] = Codec.record[AvroSchedule](
  name = "ScheduleWithHeaders",
  namespace = "com.sky.kms.domain.Schedule"
) { field =>
  (
    field("time", _.time),
    field("topic", _.topic),
    field("key", _.key),
    field("value", _.value),
    field("headers", _.headers, default = Map.empty[String, Array[Byte]].some)
  ).mapN(AvroSchedule.apply)
}

def avroBinaryDeserializer[F[_] : Sync, V : Codec]: ValueDeserializer[F, Either[ScheduleError, V]] =
  Deserializer.lift[F, Either[ScheduleError, V]] { bytes =>
    for {
      schema            <- Sync[F].fromEither(Codec[V].schema.leftMap(_.throwable))
      maybeDeserialized <-
        Sync[F].delay {
          Codec.fromBinary[V](bytes, schema).leftMap(e => ScheduleError.InvalidAvroError(schema, e.message))
        }
    } yield maybeDeserialized

  }

def avroBinarySerializer[F[_] : Sync, V : Codec]: ValueSerializer[F, V] =
  Serializer.lift[F, V](v => Sync[F].fromEither(Codec.toBinary[V](v).leftMap(_.throwable)))
