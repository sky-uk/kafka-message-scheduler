package uk.sky.scheduler.circe

import io.circe.Encoder
import io.circe.generic.semiauto
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.util.Generator.given

final class CirceSerdesSpec extends AnyWordSpec, ScalaCheckPropertyChecks, Matchers, EitherValues {

  final case class JsonScheduleWithoutHeaders(
      time: Long,
      topic: String,
      key: String,
      value: Option[String]
  )

  given scheduleEncoderWithoutHeaders: Encoder[JsonScheduleWithoutHeaders] =
    semiauto.deriveEncoder[JsonScheduleWithoutHeaders]

  given Arbitrary[JsonScheduleWithoutHeaders] = Arbitrary(Gen.resultOf(JsonScheduleWithoutHeaders.apply))

  "jsonSchedulerDecoder" should {
    "decode and encode a JSON schedule" in forAll { (schedule: JsonSchedule) =>
      decode[JsonSchedule](schedule.asJson.noSpaces).value shouldBe schedule
    }

    "decode and encode a JSON schedule without headers" in forAll { (schedule: JsonScheduleWithoutHeaders) =>
      val expected = JsonSchedule(schedule.time, schedule.topic, schedule.key, schedule.value, Map.empty)
      decode[JsonSchedule](schedule.asJson.noSpaces).value shouldBe expected
    }
  }

}
