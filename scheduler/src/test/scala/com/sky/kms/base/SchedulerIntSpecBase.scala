package com.sky.kms.base

import com.sky.kms.config.SchedulerConfig

abstract class SchedulerIntSpecBase extends AkkaKafkaSpecBase {
  implicit val conf = SchedulerConfig(Set(scheduleTopic, extraScheduleTopic), queueBufferSize = 100)
}
