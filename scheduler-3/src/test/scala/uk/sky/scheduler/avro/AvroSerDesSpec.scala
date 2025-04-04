package uk.sky.scheduler.kafka.avro

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, MonadCancelThrow}
import cats.syntax.all.*
import fs2.kafka.Headers
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{EitherValues, OptionValues}
import uk.sky.scheduler.domain.{Schedule, ScheduleV0}
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.util.ScheduleMatchers
import vulcan.generic.*
import vulcan.{AvroError, Codec}

import java.nio.charset.StandardCharsets

final class AvroSerDesSpec extends AsyncWordSpec, AsyncIOSpec, Matchers, OptionValues, EitherValues, ScheduleMatchers {

  private val scheduleWithHeaders = Schedule(
    time = Long.MinValue,
    topic = "topic",
    key = "key".getBytes(StandardCharsets.UTF_8),
    value = "value".getBytes(StandardCharsets.UTF_8).some,
    headers = Map("headerKey" -> "headerValue".getBytes(StandardCharsets.UTF_8))
  )

  private val scheduleWithoutHeaders: Schedule =
    scheduleWithHeaders.copy(headers = Map.empty[String, Array[Byte]])

  case class WritersSchedule(
      time: Long,
      topic: String,
      key: Array[Byte],
      value: Option[Array[Byte]],
      headers: Map[String, Array[Byte]]
  )

  given testScheduleCodec: Codec[WritersSchedule] = Codec.derive[WritersSchedule]

  "avro SerDes" should {

    "roundtrip valid Avro binary" in {
      final case class TestData(foo: String, bar: Int)
      val testData = TestData("bar", 1)

      given Codec[TestData] = Codec.derive[TestData]

      for {
        serialized   <- avroBinarySerializer[IO, TestData].serialize("test", Headers.empty, testData)
        deserialized <- avroBinaryDeserializer[IO, TestData].use(_.deserialize("test", Headers.empty, serialized))
      } yield deserialized.value shouldBe testData
    }
  }

  "avroBinaryDeserializer" should {
    "deserialize a Schedule with headers" in {
      for {
        avroBinary   <- Codec.toBinary[Schedule](scheduleWithHeaders).liftAvro[IO]
        deserialized <- avroBinaryDeserializer[IO, Schedule].use(_.deserialize("test", Headers.empty, avroBinary))
      } yield deserialized.value should equalSchedule(scheduleWithHeaders)
    }

    "deserialize a Schedule without headers" in {
      for {
        avroBinary   <- Codec.toBinary[Schedule](scheduleWithoutHeaders).liftAvro[IO]
        deserialized <- avroBinaryDeserializer[IO, Schedule].use(_.deserialize("test", Headers.empty, avroBinary))
      } yield deserialized.value should equalSchedule(scheduleWithoutHeaders)
    }

    "error if not valid Avro" in {
      val bytes = "foobar".getBytes(StandardCharsets.UTF_8)
      for {
        deserialized <- avroBinaryDeserializer[IO, Schedule].use(_.deserialize("test", Headers.empty, bytes))
      } yield deserialized.left.value shouldBe a[ScheduleError.InvalidAvroError]
    }

    "be able to deserialise legacy schedules" in {
      // Old Schedule data did not have headers
      case class LegacySchedule(
          time: Long,
          topic: String,
          key: Array[Byte],
          value: Option[Array[Byte]]
      )
      given legacyScheduleCodec: Codec[LegacySchedule] = Codec.derive[LegacySchedule]

      val legacySchedule: LegacySchedule = LegacySchedule(
        time = scheduleWithoutHeaders.time,
        topic = scheduleWithoutHeaders.topic,
        key = scheduleWithoutHeaders.key,
        value = scheduleWithoutHeaders.value
      )

      for {
        avroBinary   <- Codec.toBinary[LegacySchedule](legacySchedule).liftAvro[IO]
        deserialized <- avroBinaryDeserializer[IO, ScheduleV0].use(_.deserialize("test", Headers.empty, avroBinary))
      } yield deserialized.value.schedule should equalSchedule(scheduleWithoutHeaders)
    }

    "be able to deserialise writers' schedule without headers" in {

      val exampleWithoutHeaders = WritersSchedule(
        time = scheduleWithoutHeaders.time,
        topic = scheduleWithoutHeaders.topic,
        key = scheduleWithoutHeaders.key,
        value = scheduleWithoutHeaders.value,
        headers = Map.empty[String, Array[Byte]]
      )
      for {
        avroBinary   <- Codec.toBinary[WritersSchedule](exampleWithoutHeaders).liftAvro[IO]
        deserialised <- avroBinaryDeserializer[IO, Schedule].use(_.deserialize("test", Headers.empty, avroBinary))
      } yield deserialised.value should equalSchedule(scheduleWithoutHeaders)
    }

    "be able to deserialise writers' schedule with headers" in {
      val exampleWithHeaders = WritersSchedule(
        time = scheduleWithHeaders.time,
        topic = scheduleWithHeaders.topic,
        key = scheduleWithHeaders.key,
        value = scheduleWithHeaders.value,
        headers = scheduleWithHeaders.headers
      )
      for {
        avroBinary   <- Codec.toBinary[WritersSchedule](exampleWithHeaders).liftAvro[IO]
        deserialised <- avroBinaryDeserializer[IO, Schedule].use(_.deserialize("test", Headers.empty, avroBinary))
      } yield deserialised.value should equalSchedule(scheduleWithHeaders)
    }
  }

  extension [R](either: Either[AvroError, R]) {
    def liftAvro[F[_] : MonadCancelThrow]: F[R] =
      either.leftMap(avroError => TestFailedException(s"AvroError - ${avroError.message}", 0)).liftTo[F]
  }
}
