package uk.sky.scheduler.circe

import io.circe.*
import io.circe.generic.semiauto
import uk.sky.scheduler.domain.Schedule

given scheduleDecoder: Decoder[Schedule] =
  Decoder.forProduct5[Schedule, Long, String, String, Option[String], Option[Map[String, String]]](
    "time",
    "topic",
    "key",
    "value",
    "headers"
  ) { (time, topic, key, value, headers) =>
    Schedule(time, topic, key, value, headers.getOrElse(Map.empty))
  }

given scheduleEncoder: Encoder[Schedule] =
  semiauto.deriveEncoder[Schedule]
