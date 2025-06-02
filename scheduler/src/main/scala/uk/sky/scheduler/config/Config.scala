package uk.sky.scheduler.config

import cats.Show
import cats.effect.Resource
import fs2.kafka.*
import pureconfig.ConfigReader
import pureconfig.generic.semiauto
import uk.sky.BuildInfo
import uk.sky.scheduler.kafka.*

import scala.concurrent.duration.FiniteDuration

final case class Config(
    topics: TopicConfig,
    kafka: KafkaConfig
) derives ConfigReader

object Config {
  private[config] final case class Metadata(appName: String, version: String)
  val metadata: Metadata = Metadata(appName = BuildInfo.name, version = BuildInfo.version)

  given configShow: Show[Config] = { case Config(topics, kafka) =>
    s"Kafka Config: " +
      s"Avro Topics [${topics.avro.mkString(",")}]; " +
      s"Json Topics [${topics.json.mkString(",")}]; " +
      s"Broker ${kafka.consumer.bootstrapServers}"
  }
}

final case class KafkaConfig(
    consumer: ConsumerProducerConfig,
    producer: ConsumerProducerConfig,
    commit: CommitConfig
) derives ConfigReader

object KafkaConfig {
  extension (config: KafkaConfig) {
    def consumerSettings[F[_], K, V](using
        Resource[F, KeyDeserializer[F, K]],
        Resource[F, ValueDeserializer[F, V]]
    ): ConsumerSettings[F, K, V] =
      ConsumerSettings[F, K, V]
        .withBootstrapServers(config.consumer.bootstrapServers)
        .withProperties(config.consumer.properties)
        .withAutoOffsetReset(AutoOffsetReset.Earliest)

    def producerSettings[F[_], K, V](using
        Resource[F, KeySerializer[F, K]],
        Resource[F, ValueSerializer[F, V]]
    ): ProducerSettings[F, K, V] =
      ProducerSettings[F, K, V]
        .withBootstrapServers(config.producer.bootstrapServers)
        .withProperties(config.producer.properties)
        .atLeastOnce
        .performant
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

final case class TopicConfig(avro: List[String], json: List[String])

object TopicConfig {
  given ConfigReader[TopicConfig] = semiauto
    .deriveReader[TopicConfig]
    .ensure(
      config => config.avro.nonEmpty || config.json.nonEmpty,
      message = _ => "both Avro and JSON topics were empty"
    )
}
