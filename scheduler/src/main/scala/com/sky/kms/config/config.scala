package com.sky.kms.config

import akka.util.Timeout
import cats.data.{NonEmptyList, Reader}
import com.sky.kms.kafka.Topic
import com.sky.map.commons.akka.streams.BackoffRestartStrategy
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive

import scala.concurrent.duration.FiniteDuration

case class AppConfig(scheduler: SchedulerConfig)

case class SchedulerConfig(reader: ReaderConfig, publisher: PublisherConfig)

object SchedulerConfig {
  def configure: Configured[SchedulerConfig] = Reader(_.scheduler)
}

case class ReaderConfig(scheduleTopics: NonEmptyList[Topic],
                        restartStrategy: BackoffRestartStrategy,
                        offsetBatch: OffsetBatchConfig,
                        schedulingTimeout: Timeout,
                        initTimeout: Timeout)

object ReaderConfig {
  def configure: Configured[ReaderConfig] = SchedulerConfig.configure.map(_.reader)
}

case class OffsetBatchConfig(commitBatchSize: Int Refined Positive, maxCommitWait: FiniteDuration)

case class PublisherConfig(queueBufferSize: Int)

object PublisherConfig {
  def configure: Configured[PublisherConfig] = SchedulerConfig.configure.map(_.publisher)
}
