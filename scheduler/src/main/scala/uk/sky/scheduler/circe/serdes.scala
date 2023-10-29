package uk.sky.scheduler.circe

import io.circe.*
import uk.sky.scheduler.domain.Schedule

given scheduleDecoder: Decoder[Schedule] =
  Decoder.forProduct5[Schedule, Long, String, Array[Byte], Option[Array[Byte]], Option[Map[String, Array[Byte]]]](
    "time",
    "topic",
    "key",
    "value",
    "headers"
  ) { (time, topic, key, value, headers) =>
    Schedule(time, topic, key, value, headers.getOrElse(Map.empty))
  }
