package com.sky.kms.utils

import cats.data.NonEmptyList
import com.sky.kms.config._
import com.sky.kms.kafka.{AvroBinary, Topic}

import scala.concurrent.duration._

object TestConfig {
  def apply(topics: NonEmptyList[Topic]): SchedulerConfig =
    SchedulerConfig(
      ReaderConfig(topics, AvroBinary, timeouts = ReaderConfig.TimeoutConfig(100.millis, 100.millis)),
      PublisherConfig(queueBufferSize = 100),
    )
}
