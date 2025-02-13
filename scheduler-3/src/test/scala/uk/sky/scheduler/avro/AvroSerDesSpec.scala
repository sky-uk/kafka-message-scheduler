package uk.sky.scheduler.kafka.avro

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{IO, MonadCancelThrow}
import cats.syntax.all.*
import fs2.kafka.Headers
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{EitherValues, OptionValues}
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.util.ScheduleMatchers
import vulcan.generic.*
import vulcan.{AvroError, Codec}

import java.nio.charset.StandardCharsets

final class AvroSerDesSpec extends AsyncWordSpec, AsyncIOSpec, Matchers, OptionValues, EitherValues, ScheduleMatchers {

  private val scheduleWithHeaders = AvroSchedule(
    time = Long.MinValue,
    topic = "topic",
    key = "key".getBytes(StandardCharsets.UTF_8),
    value = "value".getBytes(StandardCharsets.UTF_8).some,
    optionalHeaders = Map("headerKey" -> "headerValue".getBytes(StandardCharsets.UTF_8)).some
  )

  private val scheduleWithoutHeaders: AvroSchedule =
    scheduleWithHeaders.copy(optionalHeaders = none[Map[String, Array[Byte]]])

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
        avroBinary   <- Codec.toBinary[AvroSchedule](scheduleWithHeaders).liftAvro[IO]
        deserialized <- avroBinaryDeserializer[IO, AvroSchedule].use(_.deserialize("test", Headers.empty, avroBinary))
      } yield deserialized.value should equalSchedule(scheduleWithHeaders)
    }

    "deserialize a Schedule without headers" in {
      for {
        avroBinary   <- Codec.toBinary[AvroSchedule](scheduleWithoutHeaders).liftAvro[IO]
        deserialized <- avroBinaryDeserializer[IO, AvroSchedule].use(_.deserialize("test", Headers.empty, avroBinary))
      } yield deserialized.value should equalSchedule(scheduleWithoutHeaders)
    }

    "error if not valid Avro" in {
      val bytes = "foobar".getBytes(StandardCharsets.UTF_8)
      for {
        deserialized <- avroBinaryDeserializer[IO, AvroSchedule].use(_.deserialize("test", Headers.empty, bytes))
      } yield deserialized.left.value shouldBe a[ScheduleError.InvalidAvroError]
    }
  }

  extension [R](either: Either[AvroError, R]) {
    def liftAvro[F[_] : MonadCancelThrow]: F[R] =
      either.leftMap(avroError => TestFailedException(s"AvroError - ${avroError.message}", 0)).liftTo[F]
  }
}