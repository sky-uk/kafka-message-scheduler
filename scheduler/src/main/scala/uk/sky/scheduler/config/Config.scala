package uk.sky.scheduler.config

import cats.syntax.all.*
import pureconfig.ConfigReader.Result
import pureconfig.error.CannotConvert
import pureconfig.generic.derivation.default.*
import pureconfig.{ConfigCursor, ConfigReader}
import uk.sky.scheduler.config.TopicConfig.topicConfigReader

final case class Config(scheduler: ScheduleConfig) derives ConfigReader

final case class ScheduleConfig(kafka: KafkaConfig) derives ConfigReader

final case class KafkaConfig(topics: TopicConfig, bootstrapServers: String, properties: Map[String, String])
    derives ConfigReader

final case class TopicConfig(avro: List[String], json: List[String])

object TopicConfig {

  // TODO - test this
  given topicConfigReader: ConfigReader[TopicConfig] = ConfigReader.fromCursor[TopicConfig] { cur =>
    for {
      objCur  <- cur.asObjectCursor
      avroCur <- objCur.atKey("avro")
      avroL   <- avroCur.asList
      avro    <- avroL.traverse(_.asString)
      jsonCur <- objCur.atKey("json")
      jsonL   <- jsonCur.asList
      json    <- jsonL.traverse(_.asString)
      config  <- if (avro.isEmpty && json.isEmpty) {
                   cur.failed(CannotConvert("TopicConfig", "TopicConfig", "both Avro and JSON topics were empty"))
                 } else TopicConfig(avro, json).asRight
    } yield config

  }
}
