package com.sky.kms.utils

import cats.data.NonEmptyList
import com.sky.kms.config._
import com.sky.kms.kafka.Topic
import eu.timepit.refined.auto._

import scala.concurrent.duration._

object TestConfig {
  def apply(topics: NonEmptyList[Topic]): SchedulerConfig =
    SchedulerConfig(
      ReaderConfig(
        topics,
        TestDataUtils.NoRestarts,
        OffsetBatchConfig(1, 1.milli),
        timeouts = ReaderConfig.Timeouts(100.millis, 100.millis)),
      PublisherConfig(queueBufferSize = 100),
    )
}
