package uk.sky.scheduler.circe

import io.circe.*
import io.circe.generic.semiauto
import uk.sky.scheduler.kafka.json.JsonSchedule

given jsonScheduleDecoder: Decoder[JsonSchedule] =
  Decoder.forProduct5[JsonSchedule, Long, String, String, Option[String], Option[Map[String, String]]](
    "time",
    "topic",
    "key",
    "value",
    "headers"
  ) { (time, topic, key, value, headers) =>
    JsonSchedule(time, topic, key, value, headers.getOrElse(Map.empty))
  }

given scheduleEncoder: Encoder[JsonSchedule] =
  semiauto.deriveEncoder[JsonSchedule]
