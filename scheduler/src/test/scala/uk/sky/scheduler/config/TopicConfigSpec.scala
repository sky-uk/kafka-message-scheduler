package uk.sky.scheduler.config

import cats.data.NonEmptyList
import cats.laws.discipline.arbitrary.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import pureconfig.ConfigSource

final class TopicConfigSpec extends AsyncWordSpec, ScalaCheckPropertyChecks, Matchers, EitherValues {

  "TopicConfig.topicConfigReader" should {

    extension (nel: List[String]) {

      /** List("a", "b") -> ["a", "b"]
        */
      def toConfigString: String = nel.map(s => s"\"$s\"").mkString(start = "[", sep = ", ", end = "]")
    }

    given arbValidConfigString: Arbitrary[String] = Arbitrary(Gen.alphaStr)

    given arbConfig: Arbitrary[TopicConfig] = Arbitrary {
      for {
        avroTopics <- Arbitrary.arbitrary[NonEmptyList[String]]
        jsonTopics <- Arbitrary.arbitrary[NonEmptyList[String]]
      } yield TopicConfig(avro = avroTopics.toList, json = jsonTopics.toList)
    }

    "load when both Avro and JSON topics are non empty" in forAll { (topicConfig: TopicConfig) =>
      val config = ConfigSource.string(
        s"""
             |{
             |  avro: ${topicConfig.avro.toConfigString}
             |  json: ${topicConfig.json.toConfigString}
             |}
             |""".stripMargin
      )

      config.load[TopicConfig].value shouldBe topicConfig
    }

    "load when only Avro topics are non empty" in forAll { (topicConfig: TopicConfig) =>
      val config = ConfigSource.string(
        s"""
           |{
           |  avro: ${topicConfig.avro.toConfigString}
           |  json: []
           |}
           |""".stripMargin
      )

      config.load[TopicConfig].value shouldBe topicConfig.copy(json = List.empty)
    }

    "load when only JSON topics are non empty" in forAll { (topicConfig: TopicConfig) =>
      val config = ConfigSource.string(
        s"""
           |{
           |  avro: []
           |  json: ${topicConfig.json.toConfigString}
           |}
           |""".stripMargin
      )

      config.load[TopicConfig].value shouldBe topicConfig.copy(avro = List.empty)
    }

    "error if both Avro and JSON topics are empty" in {
      val config = ConfigSource.string(
        s"""
           |{
           |  avro: []
           |  json: []
           |}
           |""".stripMargin
      )

      config.load[TopicConfig].left.value.head.description should endWith("both Avro and JSON topics were empty")
    }

  }

}
