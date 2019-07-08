package com.sky.kms.config

import cats.data.{NonEmptyList, Reader}
import com.sky.kms.kafka.Topic

import scala.concurrent.duration.FiniteDuration

case class AppConfig(scheduler: SchedulerConfig)

case class SchedulerConfig(reader: ReaderConfig, publisher: PublisherConfig)

object SchedulerConfig {
  def configure: Configured[SchedulerConfig] = Reader(_.scheduler)
}

case class ReaderConfig(scheduleTopics: NonEmptyList[Topic], timeouts: ReaderConfig.TimeoutConfig)

object ReaderConfig {
  def configure: Configured[ReaderConfig] = SchedulerConfig.configure.map(_.reader)

  case class TimeoutConfig(scheduling: FiniteDuration, initialisation: FiniteDuration)
}

case class PublisherConfig(queueBufferSize: Int)

object PublisherConfig {
  def configure: Configured[PublisherConfig] = SchedulerConfig.configure.map(_.publisher)
}
