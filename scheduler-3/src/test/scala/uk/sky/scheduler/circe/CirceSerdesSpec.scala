package uk.sky.scheduler.circe

import io.circe.parser.decode
import io.circe.syntax.*
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.util.Generator.given

final class CirceSerdesSpec extends AnyWordSpec, ScalaCheckPropertyChecks, Matchers, EitherValues {

  "jsonSchedulerDecoder" should {
    "decode and encode a JSON schedule" in forAll { (schedule: JsonSchedule) =>
      decode[JsonSchedule](schedule.asJson.noSpaces).value shouldBe schedule
    }
  }

}
