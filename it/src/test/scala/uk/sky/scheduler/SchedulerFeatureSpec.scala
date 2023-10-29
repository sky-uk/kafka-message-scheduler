package uk.sky.scheduler

import cats.syntax.all.*
import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{IO, Resource}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.FixtureAsyncWordSpec
import uk.sky.scheduler.domain.Schedule
import uk.sky.scheduler.util.KafkaUtil
import io.circe.syntax.*

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import uk.sky.scheduler.circe.given

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
      Eventually {
  override given executionContext: ExecutionContext = ExecutionContext.global

  override given patienceConfig: PatienceConfig = PatienceConfig(10.seconds)

  override val resource: Resource[IO, KafkaUtil[IO]] = KafkaUtil.apply[IO](9094, patienceConfig.timeout)

  "scheduler" should {
    "do foo" in { kafkaUtil =>
      for {
        now      <- IO.realTimeInstant.map(_.toEpochMilli)
        schedule  = Schedule(now, "output-topic", "key".getBytes, value = "value".getBytes.some, headers = Map.empty)
        _        <- kafkaUtil.produce("schedules", "key", schedule.asJson.noSpaces)
        messages <- kafkaUtil.consume("output-topic", 1)
        _        <- IO.println(s"Messages: $messages")
      } yield messages should not be empty
    }
  }

}
