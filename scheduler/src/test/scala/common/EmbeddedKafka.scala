package common

import cakesolutions.kafka.testkit.KafkaServer

object EmbeddedKafka {

  val kafkaServer = new KafkaServer

  val bootstrapServer = s"localhost:${kafkaServer.kafkaPort}"

}
