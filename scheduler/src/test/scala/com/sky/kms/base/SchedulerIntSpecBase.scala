package com.sky.kms.base

import cats.data.NonEmptyList
import com.sky.kms.config.{LoaderConfig, SchedulerConfig}
import eu.timepit.refined.auto._

import scala.concurrent.duration._

abstract class SchedulerIntSpecBase extends AkkaKafkaSpecBase {
  implicit val conf = SchedulerConfig(NonEmptyList.of(scheduleTopic, extraScheduleTopic), queueBufferSize = 100,
    LoaderConfig(idleTimeout = 2.minutes, bufferSize = 100, parallelism = 5))
}
