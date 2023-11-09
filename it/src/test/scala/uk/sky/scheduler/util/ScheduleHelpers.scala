package uk.sky.scheduler.util

import java.nio.charset.StandardCharsets

import cats.effect.IO
import cats.syntax.all.*
import fs2.kafka.ValueSerializer
import uk.sky.scheduler.circe.scheduleEncoder
import uk.sky.scheduler.kafka.avro.{avroBinarySerializer, avroScheduleCodec, AvroSchedule}
import uk.sky.scheduler.kafka.json.{jsonSerializer, JsonSchedule}
import uk.sky.scheduler.syntax.all.*

trait ScheduleHelpers {
  def createJsonSchedule(
      time: Long,
      topic: String,
      key: String,
      value: String,
      headers: Map[String, String] = Map.empty
  ): JsonSchedule =
    JsonSchedule(
      time = time,
      topic = topic,
      key = key.base64Encode,
      value = value.base64Encode.some,
      headers = headers
    )

  def createAvroSchedule(
      time: Long,
      topic: String,
      key: String,
      value: String,
      headers: Map[String, Array[Byte]] = Map.empty
  ): AvroSchedule =
    AvroSchedule(
      time = time,
      topic = topic,
      key = key.getBytes(StandardCharsets.UTF_8),
      value = value.getBytes(StandardCharsets.UTF_8).some,
      headers = headers
    )

  given ValueSerializer[IO, JsonSchedule] = jsonSerializer[IO, JsonSchedule]
  given ValueSerializer[IO, AvroSchedule] = avroBinarySerializer[IO, AvroSchedule]
}
