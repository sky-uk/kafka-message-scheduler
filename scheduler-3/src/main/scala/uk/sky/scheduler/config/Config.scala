package uk.sky.scheduler.config

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

final case class Config(
  kafka: KafkaConfig
) derives ConfigReader

final case class KafkaConfig(
  consumer: ConsumerProducerConfig,
  producer: ConsumerProducerConfig,
  commit: CommitConfig
) derives ConfigReader

final case class ConsumerProducerConfig(
  bootstrapServers: String,
  properties: Map[String, String]
) derives ConfigReader

final case class CommitConfig(
  maxBatch: Int,
  maxInterval: FiniteDuration
) derives ConfigReader