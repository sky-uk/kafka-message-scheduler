package com.sky.kms.config

import cats.data.{NonEmptyList, Reader}
import com.sky.kms.kafka.Topic
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

import scala.concurrent.duration.FiniteDuration

case class AppConfig(scheduler: SchedulerConfig)

case class SchedulerConfig(scheduleTopics: NonEmptyList[Topic], queueBufferSize: Int, topicLoader: LoaderConfig, offsetBatch: OffsetBatchConfig)

object SchedulerConfig {
  def configure: Configured[SchedulerConfig] = Reader(_.scheduler)
}

case class LoaderConfig(idleTimeout: FiniteDuration, bufferSize: Int Refined Positive, parallelism: Int Refined Positive)

case class OffsetBatchConfig(commitBatchSize: Int Refined Positive, maxCommitWait: FiniteDuration)
