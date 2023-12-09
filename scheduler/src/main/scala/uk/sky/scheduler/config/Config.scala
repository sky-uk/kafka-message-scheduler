package uk.sky.scheduler.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.record.CompressionType
import pureconfig.ConfigReader.Result
import pureconfig.generic.derivation.default.*
import pureconfig.{ConfigCursor, ConfigReader}
import uk.sky.scheduler.config.TopicConfig.topicConfigReader

final case class Config(scheduler: ScheduleConfig) derives ConfigReader

final case class ScheduleConfig(kafka: KafkaConfig) derives ConfigReader

final case class KafkaConfig(topics: TopicConfig, bootstrapServers: String, properties: Map[String, String])
    derives ConfigReader

object KafkaConfig {
  def atLeastOnceProducerProperties: Map[String, String] = Map(
    ProducerConfig.LINGER_MS_CONFIG                      -> "10",
    ProducerConfig.BATCH_SIZE_CONFIG                     -> "1000000",
    ProducerConfig.RETRIES_CONFIG                        -> Int.MaxValue.toString,
    ProducerConfig.ACKS_CONFIG                           -> "all",
    ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION -> "1",
    ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG             -> "false",
    ProducerConfig.BUFFER_MEMORY_CONFIG                  -> "80000000",
    ProducerConfig.COMPRESSION_TYPE_CONFIG               -> CompressionType.ZSTD.name
  )
}

final case class TopicConfig(avro: List[String], json: List[String])

object TopicConfig {

  given topicConfigReader: ConfigReader[TopicConfig] =
    ConfigReader
      .forProduct2[TopicConfig, List[String], List[String]]("avro", "json")(TopicConfig.apply)
      .ensure(
        config => config.avro.nonEmpty || config.json.nonEmpty,
        _ => "both Avro and JSON topics were empty"
      )

}
