package uk.sky.scheduler.kafka.json

import java.nio.charset.StandardCharsets

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.syntax.all.*
import fs2.kafka.Headers
import io.circe.generic.semiauto
import io.circe.syntax.*
import io.circe.{Codec, Encoder, Json}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{EitherValues, OptionValues}
import uk.sky.scheduler.circe.given
import uk.sky.scheduler.converters.base64.*
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.util.ScheduleMatchers

final class JsonSerDesSpec extends AsyncWordSpec, AsyncIOSpec, Matchers, OptionValues, EitherValues, ScheduleMatchers {

  val schedule = JsonSchedule(
    time = Long.MinValue,
    topic = "topic",
    key = "key".base64Encode,
    value = "value".base64Encode.some,
    headers = Map("headerKey" -> "headerValue".base64Encode)
  )

  object ScheduleNoHeaders {
    val emptyJson: Json               = schedule.asJson.hcursor.downField("headers").delete.top.value
    val emptyHeaderBytes: Array[Byte] = emptyJson.noSpaces.getBytes(StandardCharsets.UTF_8)

    val nullJson: Json               = schedule.asJson.hcursor.downField("headers").set(Json.Null).top.value
    val nullHeaderBytes: Array[Byte] = nullJson.noSpaces.getBytes(StandardCharsets.UTF_8)
  }

  "json SerDes" should {

    "roundtrip valid JSON" in {
      final case class TestData(foo: String, bar: Int)
      val testData          = TestData("bar", 1)
      given Codec[TestData] = semiauto.deriveCodec

      for {
        serialized   <- jsonSerializer[IO, TestData].serialize("test", Headers.empty, testData)
        deserialized <- jsonDeserializer[IO, TestData].deserialize("test", Headers.empty, serialized)
      } yield deserialized.value shouldBe testData
    }

    "deserialize a schedule" in {
      val json = schedule.asJson.noSpaces.getBytes(StandardCharsets.UTF_8)
      for {
        deserialized <- jsonDeserializer[IO, JsonSchedule].deserialize("test", Headers.empty, json)
      } yield deserialized.value should equalSchedule(schedule)
    }

    "set the headers to an empty Map if not present" in {
      for {
        deserialized <-
          jsonDeserializer[IO, JsonSchedule].deserialize("test", Headers.empty, ScheduleNoHeaders.emptyHeaderBytes)
      } yield deserialized.value.headers shouldBe empty
    }

    "set the headers to an empty Map if null" in {
      for {
        deserialized <-
          jsonDeserializer[IO, JsonSchedule].deserialize("test", Headers.empty, ScheduleNoHeaders.nullHeaderBytes)
      } yield deserialized.value.headers shouldBe empty
    }

    "error if the payload is not valid JSON" in {
      for {
        deserialized <-
          jsonDeserializer[IO, JsonSchedule].deserialize(
            "test",
            Headers.empty,
            "foobar".getBytes(StandardCharsets.UTF_8)
          )
      } yield deserialized.left.value shouldBe a[ScheduleError.NotJsonError]
    }

    "error if the payload is not the given JSON type" in {
      for {
        deserialized <-
          jsonDeserializer[IO, JsonSchedule].deserialize(
            "test",
            Headers.empty,
            Json.obj("foo" -> "bar".asJson).noSpaces.getBytes(StandardCharsets.UTF_8)
          )
      } yield deserialized.left.value shouldBe a[ScheduleError.InvalidJsonError]
    }
  }

}
