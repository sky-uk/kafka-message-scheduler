package uk.sky.scheduler.config

import buildinfo.BuildInfo
import cats.effect.{Resource, Sync}
import fs2.kafka.*
import pureconfig.ConfigReader.Result
import pureconfig.generic.derivation.default.*
import pureconfig.{ConfigCursor, ConfigReader}
import uk.sky.scheduler.config.TopicConfig.topicConfigReader

import scala.concurrent.duration.FiniteDuration

final case class Config(kafka: KafkaConfig) derives ConfigReader

object Config {
  private[config] final case class Metadata(appName: String, version: String)

  val metadata: Metadata = Metadata(appName = BuildInfo.name, version = BuildInfo.version)
}

final case class KafkaConfig(
    topics: TopicConfig,
    bootstrapServers: String,
    properties: Map[String, String],
    producer: KafkaProducerConfig
) derives ConfigReader

object KafkaConfig {
  extension (config: KafkaConfig) {
    def consumerSettings[F[_] : Sync, K, V](using
        Resource[F, KeyDeserializer[F, K]],
        Resource[F, ValueDeserializer[F, V]]
    ): ConsumerSettings[F, K, V] =
      ConsumerSettings[F, K, V]
        .withBootstrapServers(config.bootstrapServers)
        .withProperties(config.properties)
        .withAutoOffsetReset(AutoOffsetReset.Earliest)

    def producerSettings[F[_] : Sync, K, V](using
        Resource[F, KeySerializer[F, K]],
        Resource[F, ValueSerializer[F, V]]
    ): ProducerSettings[F, K, V] =
      ProducerSettings[F, K, V]
        .withBootstrapServers(config.bootstrapServers)
        .withProperties(config.properties)
  }
}

final case class TopicConfig(avro: List[String], json: List[String])

object TopicConfig {
  given topicConfigReader: ConfigReader[TopicConfig] =
    ConfigReader
      .forProduct2[TopicConfig, List[String], List[String]]("avro", "json")(TopicConfig.apply)
      .ensure(
        config => config.avro.nonEmpty || config.json.nonEmpty,
        message = _ => "both Avro and JSON topics were empty"
      )
}

final case class KafkaProducerConfig(batchSize: Int, timeout: FiniteDuration) derives ConfigReader
