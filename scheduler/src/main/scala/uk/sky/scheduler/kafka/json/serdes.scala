package uk.sky.scheduler.kafka.json

import cats.effect.Sync
import fs2.kafka.{Deserializer, Serializer, ValueSerializer}
import io.circe.syntax.*
import io.circe.{parser, Decoder, Encoder, Json}

def jsonSerializer[F[_] : Sync, V : Encoder]: ValueSerializer[F, V] =
  Serializer.string[F].contramap[V](_.asJson.noSpaces)

def jsonDeserializer[F[_] : Sync, T : Decoder]: Deserializer[F, T] = for {
  payload <- Deserializer.string[F]
  json    <- parser.parse(payload).fold(Deserializer.fail[F, Json], Deserializer.const[F, Json])
  decoded <- json.as[T].fold(Deserializer.fail[F, T], Deserializer.const[F, T])
} yield decoded
