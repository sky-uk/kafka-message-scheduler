package uk.sky.scheduler.kafka.avro

import java.nio.charset.StandardCharsets

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

final class AvroSerDesSpec extends AsyncWordSpec, AsyncIOSpec, Matchers, OptionValues, EitherValues, ScheduleMatchers {

  val scheduleWithHeaders = AvroSchedule(
    time = Long.MinValue,
    topic = "topic",
    key = "key".getBytes(StandardCharsets.UTF_8),
    value = "value".getBytes(StandardCharsets.UTF_8).some,
    headers = Map("headerKey" -> "headerValue".getBytes(StandardCharsets.UTF_8))
  )

  "avro SerDes" when {

    "avroBinaryDeserializer" should {
      "roundtrip valid Avro binary" in {
        final case class TestData(foo: String, bar: Int)
        val testData          = TestData("bar", 1)
        given Codec[TestData] = Codec.derive[TestData]

        for {
          serialized   <- avroBinarySerializer[IO, TestData].serialize("test", Headers.empty, testData)
          deserialized <- avroBinaryDeserializer[IO, TestData].deserialize("test", Headers.empty, serialized)
        } yield deserialized.value shouldBe testData
      }
    }

    "deserialize a Schedule" in {
      for {
        avroBinary   <- Codec.toBinary[AvroSchedule](scheduleWithHeaders).liftAvro[IO]
        deserialized <- avroBinaryDeserializer[IO, AvroSchedule].deserialize("test", Headers.empty, avroBinary)
      } yield deserialized.value should equalSchedule(scheduleWithHeaders)
    }

    "error if not valid Avro" in {
      val bytes = "foobar".getBytes(StandardCharsets.UTF_8)
      for {
        deserialized <- avroBinaryDeserializer[IO, AvroSchedule].deserialize("test", Headers.empty, bytes)
      } yield deserialized.left.value shouldBe a[ScheduleError.InvalidAvroError]
    }

  }

  extension [R](either: Either[AvroError, R]) {
    def liftAvro[F[_] : MonadCancelThrow]: F[R] = MonadCancelThrow[F].fromEither(
      either.leftMap(avroError => TestFailedException(s"AvroError - ${avroError.message}", 0))
    )
  }
}
