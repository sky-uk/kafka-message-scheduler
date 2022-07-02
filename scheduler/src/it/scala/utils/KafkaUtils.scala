package utils

import io.github.embeddedkafka.EmbeddedKafkaConfig

trait KafkaUtils {
  implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = 9093)
  val outputTopic                               = "output.topic"
}
