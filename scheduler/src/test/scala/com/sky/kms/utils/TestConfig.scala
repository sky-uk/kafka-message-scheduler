package com.sky.kms.utils

import cats.data.NonEmptyList
import com.sky.kms.config.{LoaderConfig, OffsetBatchConfig, PublisherConfig, ReaderConfig, SchedulerConfig}
import com.sky.kms.kafka.Topic
import eu.timepit.refined.auto._

import scala.concurrent.duration._

object TestConfig {
  def apply(topics: NonEmptyList[Topic]): SchedulerConfig =
    SchedulerConfig(
      ReaderConfig(
        topics,
        LoaderConfig(idleTimeout = 2.minutes, bufferSize = 100, parallelism = 5),
        TestDataUtils.NoRestarts,
        OffsetBatchConfig(1, 1.milli)),
      PublisherConfig(queueBufferSize = 100),
    )
}
