package uk.sky.scheduler.config

import cats.Show
import cats.syntax.all.*
import io.circe.Encoder
import io.circe.generic.semiauto
import io.circe.syntax.*
import monocle.syntax.all.*
import org.apache.kafka.common.config.SslConfigs
import pureconfig.ConfigReader.Result
import pureconfig.error.CannotConvert
import pureconfig.generic.derivation.default.*
import pureconfig.{ConfigCursor, ConfigReader}
import uk.sky.scheduler.config.TopicConfig.topicConfigReader

final case class Config(scheduler: ScheduleConfig) derives ConfigReader

object Config {
  private val redacted = "*".repeat(8)

  given kafkaConfigEncoder: Encoder[KafkaConfig] = semiauto
    .deriveEncoder[KafkaConfig]
    .contramap(
      _.focus(_.properties)
        .modify(
          _.updatedWith(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG)(_.as(redacted))
            .updatedWith(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG)(_.as(redacted))
            .updatedWith(SslConfigs.SSL_KEY_PASSWORD_CONFIG)(_.as(redacted))
        )
    )

  given schedulerConfigEncoder: Encoder[ScheduleConfig] = semiauto.deriveEncoder[ScheduleConfig]
  given configEncoder: Encoder[Config]                  = semiauto.deriveEncoder[Config]

  given configShow: Show[Config] = Show.show(_.asJson.noSpaces)
}

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
