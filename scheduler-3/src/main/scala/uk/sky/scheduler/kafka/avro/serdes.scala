package uk.sky.scheduler.kafka.avro

import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import fs2.kafka.{Deserializer, Serializer, ValueDeserializer, ValueSerializer}
import org.apache.avro.Schema
import uk.sky.scheduler.error.ScheduleError
import vulcan.Codec

given avroScheduleCodec: Codec[AvroSchedule] = Codec.record[AvroSchedule](
  name = "Schedule",
  namespace = "uk.sky.scheduler.domain"
)(field =>
  (
    field("time", _.time, doc = "The time to execute the Schedule, in epoch milliseconds.".some),
    field("topic", _.topic, doc = "The topic to send the Schedule to.".some),
    field("key", _.key, doc = "The key identifying the payload.".some),
    field("value", _.value, doc = "The payload to be sent. null indicates a tombstone.".some),
    field("headers", _.optionalHeaders, doc = "Optional extra metadata to send with the payload.".some)
  ).mapN(AvroSchedule.apply)
)

def avroBinaryDeserializer[F[_] : Sync, V : Codec]: Resource[F, ValueDeserializer[F, Either[ScheduleError, V]]] =
  Codec[V].schema match {
    case Left(error)   => Resource.raiseError(error.throwable)
    case Right(schema) => Resource.pure(avroBinaryDeserializer(schema))
  }

def avroBinaryDeserializer[F[_] : Sync, V : Codec](schema: Schema): ValueDeserializer[F, Either[ScheduleError, V]] =
  Deserializer.lift[F, Either[ScheduleError, V]](bytes =>
    Sync[F].delay(
      Codec
        .fromBinary[V](bytes, schema)
        .leftMap(e => ScheduleError.InvalidAvroError(schema, e.throwable))
    )
  )

def avroBinarySerializer[F[_] : Sync, V : Codec]: ValueSerializer[F, V] =
  Serializer.lift[F, V](
    Codec
      .toBinary[V](_)
      .leftMap(_.throwable)
      .liftTo[F]
  )
