package uk.sky.scheduler.kafka.avro

import cats.effect.{Resource, Sync}
import cats.syntax.all.*
import fs2.kafka.{Deserializer, Serializer, ValueDeserializer, ValueSerializer}
import org.apache.avro.Schema
import uk.sky.scheduler.error.ScheduleError
import vulcan.Codec
import vulcan.generic.*

given avroScheduleCodec: Codec[AvroSchedule]                             = Codec.derive[AvroSchedule]
given avroScheduleWithoutHeadersCodec: Codec[AvroScheduleWithoutHeaders] = Codec.derive[AvroScheduleWithoutHeaders]

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
