package uk.sky.scheduler.kafka.json

import cats.effect.Sync
import cats.syntax.all.*
import fs2.kafka.{Deserializer, Serializer, ValueDeserializer, ValueSerializer}
import io.circe.syntax.*
import io.circe.{parser, Decoder, Encoder}
import uk.sky.scheduler.error.ScheduleError

def jsonDeserializer[F[_] : Sync, T : Decoder]: ValueDeserializer[F, Either[ScheduleError, T]] =
  for {
    payload <- Deserializer.string[F]
  } yield for {
    json    <- parser.parse(payload).leftMap(e => ScheduleError.NotJsonError(payload, e))
    decoded <- json.as[T].leftMap(e => ScheduleError.InvalidJsonError(json.noSpaces, e))
  } yield decoded

def jsonSerializer[F[_] : Sync, V : Encoder]: ValueSerializer[F, V] =
  Serializer.string[F].contramap[V](_.asJson.noSpaces)
