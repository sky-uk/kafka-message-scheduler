package uk.sky.scheduler

import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{Clock, IO, Resource}
import cats.syntax.all.*
import org.scalatest.wordspec.FixtureAsyncWordSpec
import org.scalatest.{LoneElement, OptionValues}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import uk.sky.scheduler.util.{KafkaUtil, ScheduleHelpers}
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.syntax.all.*
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

final class SchedulerFeatureSpec
    extends FixtureAsyncWordSpec,
      AsyncIOSpec,
      CatsResourceIO[KafkaUtil[IO]],
      ScheduleHelpers,
      OptionValues,
      Matchers,
      Eventually,
      LoneElement {
  val timeout: FiniteDuration = 30.seconds
  val kafkaPort               = 9094

  override given executionContext: ExecutionContext = ExecutionContext.global
  override given patienceConfig: PatienceConfig     = PatienceConfig(timeout)
  override val ResourceTimeout: Duration            = timeout

  override def resource: Resource[IO, KafkaUtil[IO]] = Resource.pure(KafkaUtil[IO](kafkaPort, timeout))

  "scheduler" should {
    "schedule a Json event for the specified time" in { kafkaUtil =>
      val outputTopic     = "output-topic"
      val outputJsonKey   = "jsonKey"
      val outputJsonValue = "jsonValue"

      for {
        scheduledTime <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule       = createJsonSchedule(scheduledTime, outputTopic, outputJsonKey, outputJsonValue)
        _             <- kafkaUtil.produce[JsonSchedule]("json-schedules", "input-key-json" -> schedule.some)
        messages      <- kafkaUtil.consume[String](outputTopic, 1)
      } yield {
        val message = messages.loneElement
        message.keyValue shouldBe outputJsonKey -> outputJsonValue
        message.producedAt.toEpochMilli shouldBe scheduledTime +- 100L
      }
    }

  }

}
