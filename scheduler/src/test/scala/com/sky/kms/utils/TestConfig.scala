package com.sky.kms.utils

import cats.data.NonEmptyList
import com.sky.kms.config.*
import com.sky.kms.kafka.Topic

import scala.concurrent.duration.*

object TestConfig {
  def apply(topics: NonEmptyList[Topic]): SchedulerConfig =
    SchedulerConfig(
      ReaderConfig(topics, timeouts = ReaderConfig.TimeoutConfig(100.millis, 100.millis)),
      PublisherConfig(queueBufferSize = 100)
    )
}
