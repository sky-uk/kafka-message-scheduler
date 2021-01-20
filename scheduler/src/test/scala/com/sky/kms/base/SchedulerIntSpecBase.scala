package com.sky.kms.base

import cats.data.NonEmptyList
import com.sky.kms.utils.TestConfig
import scala.concurrent.duration._

abstract class SchedulerIntSpecBase extends AkkaKafkaSpecBase {
  implicit val conf = TestConfig(NonEmptyList.of(scheduleTopic, extraScheduleTopic))
  val tolerance     = 1300 milliseconds
}
