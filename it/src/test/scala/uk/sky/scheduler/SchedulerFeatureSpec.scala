package uk.sky.scheduler

import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{IO, Resource}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.FixtureAsyncWordSpec
import uk.sky.scheduler.util.KafkaUtil

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

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
        _        <- kafkaUtil.produce("schedules", "key", "value")
        messages <- kafkaUtil.consume("output-topic", 1)
        _        <- IO.println(s"Messages: $messages")
      } yield messages should not be empty
    }
  }

}
