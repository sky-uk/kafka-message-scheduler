package com.sky.kms.base

import cats.data.NonEmptyList
import com.sky.kms.config.SchedulerConfig
import com.sky.kms.utils.TestConfig

import scala.concurrent.duration._

abstract class SchedulerIntSpecBase extends AkkaKafkaSpecBase {
  implicit val conf: SchedulerConfig = TestConfig(NonEmptyList.of(scheduleTopic, extraScheduleTopic))
  val tolerance: FiniteDuration      = 1300 milliseconds
}
