package uk.sky.scheduler

import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import io.circe.syntax.*
import org.scalatest.LoneElement
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.FixtureAsyncWordSpec
import uk.sky.scheduler.circe.given
import uk.sky.scheduler.domain.Schedule
import uk.sky.scheduler.syntax.all.*
import uk.sky.scheduler.util.KafkaUtil

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/*
TODO - investigate why dockerComposeTest gives
Error occurred during initialization of VM
java.lang.Error: Properties init: Could not determine current working directory.
 */
final class SchedulerFeatureSpec
    extends FixtureAsyncWordSpec,
      AsyncIOSpec,
      CatsResourceIO[KafkaUtil[IO]],
      Matchers,
      Eventually,
      LoneElement {
  override given executionContext: ExecutionContext = ExecutionContext.global

  val timeout                                   = 30.seconds
  override given patienceConfig: PatienceConfig = PatienceConfig(timeout = timeout)

  override val resource: Resource[IO, KafkaUtil[IO]] = KafkaUtil.apply[IO](9094, timeout)

  "scheduler" should {
    "schedule an event for the specified time" in { kafkaUtil =>
      def createSchedule(time: Long, topic: String, key: String, value: String): IO[Schedule] =
        for {
          key   <- key.base64Encode[IO]
          value <- value.base64Encode[IO]
        } yield Schedule(
          time = time,
          topic = topic,
          key = key,
          value = value.some,
          headers = Map.empty
        )

      for {
        scheduledTime <- IO.realTimeInstant.map(_.plusSeconds(20).toEpochMilli)
        schedule      <- createSchedule(scheduledTime, "output-topic", "scheduledKey", "scheduledValue")
        schedule2     <- createSchedule(scheduledTime, "output-topic", "cancellableKey", "scheduledValue")
        _             <- IO.println(s"schedule: $schedule")
        _             <- IO.println(s"Schedule JSON: ${schedule.asJson.spaces2}")
        _             <- kafkaUtil.produce("schedules", "key", schedule.asJson.noSpaces.some)
        // Cancelled key isn't under test this is just PoC
        _             <- kafkaUtil.produce("schedules", "cancelledKey", schedule2.asJson.noSpaces.some)
        _             <- IO.sleep(5.seconds)
        _             <- kafkaUtil.produce("schedules", "cancelledKey", none)
        messages      <- kafkaUtil.consume("output-topic", 1)
        _             <- IO.println(s"Messages: $messages")
      } yield {
        val message = messages.loneElement
        message.keyValue shouldBe ("scheduledKey" -> "scheduledValue")
        message.producedAt.toEpochMilli shouldBe scheduledTime +- 100
      }
    }
  }

}
