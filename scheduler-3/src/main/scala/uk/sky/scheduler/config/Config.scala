package uk.sky.scheduler.config

import cats.effect.{Resource, Sync}
import fs2.kafka.*
import pureconfig.ConfigReader
import uk.sky.BuildInfo

import scala.concurrent.duration.FiniteDuration

final case class Config(
    kafka: KafkaConfig,
    scheduler: SchedulerConfig
) derives ConfigReader

object Config {
  private[config] final case class Metadata(appName: String, version: String)
  val metadata: Metadata = Metadata(appName = BuildInfo.name, version = BuildInfo.version)
}

final case class KafkaConfig(
    consumer: ConsumerProducerConfig,
    producer: ConsumerProducerConfig,
    commit: CommitConfig
) derives ConfigReader

object KafkaConfig {
  extension (config: KafkaConfig) {
    def consumerSettings[F[_] : Sync, K, V](using
        Resource[F, KeyDeserializer[F, K]],
        Resource[F, ValueDeserializer[F, V]]
    ): ConsumerSettings[F, K, V] =
      ConsumerSettings[F, K, V]
        .withBootstrapServers(config.consumer.bootstrapServers)
        .withProperties(config.consumer.properties)
        .withAutoOffsetReset(AutoOffsetReset.Earliest)

    def producerSettings[F[_] : Sync, K, V](using
        Resource[F, KeySerializer[F, K]],
        Resource[F, ValueSerializer[F, V]]
    ): ProducerSettings[F, K, V] =
      ProducerSettings[F, K, V]
        .withBootstrapServers(config.producer.bootstrapServers)
        .withProperties(config.producer.properties)
  }
}

final case class ConsumerProducerConfig(
    bootstrapServers: String,
    properties: Map[String, String]
) derives ConfigReader

final case class CommitConfig(
    maxBatch: Int,
    maxInterval: FiniteDuration
) derives ConfigReader

final case class SchedulerConfig(
    reader: ReaderConfig
) derives ConfigReader

final case class ReaderConfig(
    scheduleTopics: List[String],
    kafkaBrokers: String
) derives ConfigReader

object ReaderConfig {
  given readerConfigReader: ConfigReader[ReaderConfig] =
    ConfigReader
      .forProduct2[ReaderConfig, List[String], String]("scheduleTopics", "kafkaBrokers")(ReaderConfig.apply)
      .ensure(
        readerConfig => readerConfig.scheduleTopics.nonEmpty,
        message = _ => "Schedule topics are empty"
      )
}
