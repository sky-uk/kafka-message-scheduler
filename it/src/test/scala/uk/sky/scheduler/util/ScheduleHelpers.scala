package uk.sky.scheduler.util

import java.nio.charset.StandardCharsets

import cats.effect.IO
import cats.syntax.all.*
import fs2.kafka.ValueSerializer
import uk.sky.scheduler.circe.scheduleEncoder
import uk.sky.scheduler.converters.base64.*
import uk.sky.scheduler.kafka.avro.{
  avroBinarySerializer,
  avroScheduleCodec,
  avroScheduleWithoutHeadersCodec,
  AvroSchedule,
  AvroScheduleWithoutHeaders
}
import uk.sky.scheduler.kafka.json.{jsonSerializer, JsonSchedule}
import vulcan.Codec
import vulcan.generic.*

case class TestAvroSchedule(
    time: Long,
    topic: String,
    key: Array[Byte],
    value: Option[Array[Byte]],
    headers: Map[String, Array[Byte]]
)

case class TestAvroScheduleNoHeaders(
    time: Long,
    topic: String,
    key: Array[Byte],
    value: Option[Array[Byte]]
)

trait ScheduleHelpers {

  def createJsonSchedule(
      time: Long,
      topic: String,
      key: String,
      value: String,
      headers: Map[String, String] = Map.empty
  ): JsonSchedule =
    JsonSchedule(time = time, topic = topic, key = key.base64Encode, value = value.base64Encode.some, headers = headers)

  def createTestAvroSchedule(
      time: Long,
      topic: String,
      key: String,
      value: String,
      headers: Map[String, Array[Byte]] = Map.empty[String, Array[Byte]]
  ): TestAvroSchedule = TestAvroSchedule(
    time,
    topic,
    key.getBytes(StandardCharsets.UTF_8),
    value.getBytes(StandardCharsets.UTF_8).some,
    headers
  )

  def createTestAvroScheduleWithoutHeaders(
      time: Long,
      topic: String,
      key: String,
      value: String
  ): TestAvroScheduleNoHeaders = TestAvroScheduleNoHeaders(
    time,
    topic,
    key.getBytes(StandardCharsets.UTF_8),
    value.getBytes(StandardCharsets.UTF_8).some
  )

  given Codec[TestAvroSchedule]          = Codec.derive[TestAvroSchedule]
  given Codec[TestAvroScheduleNoHeaders] = Codec.derive[TestAvroScheduleNoHeaders]

  given ValueSerializer[IO, JsonSchedule]               = jsonSerializer[IO, JsonSchedule]
  given ValueSerializer[IO, AvroSchedule]               = avroBinarySerializer[IO, AvroSchedule]
  given ValueSerializer[IO, AvroScheduleWithoutHeaders] = avroBinarySerializer[IO, AvroScheduleWithoutHeaders]
  given ValueSerializer[IO, TestAvroSchedule]           = avroBinarySerializer[IO, TestAvroSchedule]
  given ValueSerializer[IO, TestAvroScheduleNoHeaders]  = avroBinarySerializer[IO, TestAvroScheduleNoHeaders]
}
