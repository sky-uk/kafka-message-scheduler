package uk.sky.scheduler.util

import cats.effect.IO
import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.FixtureAsyncWordSpec
import org.scalatest.{LoneElement, OptionValues}
import pureconfig.*

trait SchedulerFeatureBase
    extends FixtureAsyncWordSpec,
      AsyncIOSpec,
      CatsResourceIO[KafkaUtil[IO]],
      ScheduleHelpers,
      OptionValues,
      Matchers,
      Eventually,
      LoneElement {

  final case class KafkaConfig(bootstrapServer: String, timeout: Long, groupId: String) derives ConfigReader

  lazy val config = ConfigSource.resources("kafka.conf").loadOrThrow[KafkaConfig]
}
