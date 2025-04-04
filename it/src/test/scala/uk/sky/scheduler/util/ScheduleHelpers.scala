package uk.sky.scheduler.util

import cats.effect.IO
import cats.syntax.all.*
import fs2.kafka.ValueSerializer
import uk.sky.scheduler.circe.scheduleEncoder
import uk.sky.scheduler.converters.base64.*
import uk.sky.scheduler.kafka.avro.{avroBinarySerializer, avroScheduleCodec}
import uk.sky.scheduler.kafka.json.{jsonSerializer, JsonSchedule}

import java.nio.charset.StandardCharsets

trait ScheduleHelpers {

  def createJsonSchedule(
      time: Long,
      topic: String,
      key: String,
      value: String,
      headers: Map[String, String] = Map.empty
  ): JsonSchedule =
    JsonSchedule(time = time, topic = topic, key = key.base64Encode, value = value.base64Encode.some, headers = headers)

  def createAvroSchedule(
      time: Long,
      topic: String,
      key: String,
      value: String,
      headers: Map[String, Array[Byte]] = Map.empty[String, Array[Byte]]
  ): Schedule = Schedule(
    time,
    topic,
    key.getBytes(StandardCharsets.UTF_8),
    value.getBytes(StandardCharsets.UTF_8).some,
    headers
  )

  given ValueSerializer[IO, JsonSchedule] = jsonSerializer[IO, JsonSchedule]
  given ValueSerializer[IO, Schedule]     = avroBinarySerializer[IO, Schedule]
}
