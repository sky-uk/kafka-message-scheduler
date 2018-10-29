package com.sky.kms.base

import cats.data.NonEmptyList
import com.sky.kms.utils.TestConfig

abstract class SchedulerIntSpecBase extends AkkaKafkaSpecBase {
  implicit val conf = TestConfig(NonEmptyList.of(scheduleTopic, extraScheduleTopic))
}
