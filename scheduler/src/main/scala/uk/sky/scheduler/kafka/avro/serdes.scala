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
    field("time", _.time, doc = "The time to execute the Schedule, in epoch milliseconds.".some),
    field("topic", _.topic, doc = "The topic to send the Schedule to.".some),
    field("key", _.key, doc = "The key identifying the payload.".some),
    field("value", _.value, doc = "The payload to be sent. null indicates a tombstone.".some),
    field("headers", _.optionalHeaders, doc = "Optional extra metadata to send with the payload.".some)
  ).mapN(AvroSchedule.apply)
}

def avroBinaryDeserializer[F[_] : Sync, V : Codec]: ValueDeserializer[F, Either[ScheduleError, V]] =
  Deserializer.lift[F, Either[ScheduleError, V]] { bytes =>
    for {
      schema            <- Sync[F].defer {
                             Codec[V].schema
                               .leftMap(_.throwable)
                               .liftTo[F]
                           }
      maybeDeserialized <- Sync[F].delay {
                             Codec
                               .fromBinary[V](bytes, schema)
                               .leftMap(e => ScheduleError.InvalidAvroError(schema, e.message))
                           }
    } yield maybeDeserialized

  }

def avroBinarySerializer[F[_] : Sync, V : Codec]: ValueSerializer[F, V] =
  Serializer.lift[F, V] { avro =>
    Sync[F].defer {
      Codec
        .toBinary[V](avro)
        .leftMap(_.throwable)
        .liftTo[F]
    }
  }
