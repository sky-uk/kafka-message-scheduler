package uk.sky.scheduler.config

import cats.Show
import cats.syntax.all.*
import io.circe.Encoder
import io.circe.generic.semiauto
import io.circe.syntax.*
import monocle.syntax.all.*
import org.apache.kafka.common.config.SslConfigs

import scala.concurrent.duration.FiniteDuration

private val Redacted = "*".repeat(8)

private given topicConfigEncoder: Encoder[TopicConfig] =
  semiauto.deriveEncoder[TopicConfig]

private given kafkaProducerConfigEncoder: Encoder[KafkaProducerConfig] =
  semiauto.deriveEncoder[KafkaProducerConfig]

private given kafkaConfigEncoder: Encoder[KafkaConfig] = semiauto
  .deriveEncoder[KafkaConfig]
  .contramap(
    _.focus(_.properties)
      .modify(
        _.updatedWith(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG)(_.as(Redacted))
          .updatedWith(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG)(_.as(Redacted))
          .updatedWith(SslConfigs.SSL_KEY_PASSWORD_CONFIG)(_.as(Redacted))
      )
  )

private given configFiniteDurationEncoder: Encoder[FiniteDuration] =
  Encoder.instance(fd => s"${fd.length} ${fd.unit.toString.toLowerCase}".asJson)

private given schedulerConfigEncoder: Encoder[ScheduleConfig] = semiauto.deriveEncoder[ScheduleConfig]

private given configEncoder: Encoder[Config] = semiauto.deriveEncoder[Config]

given configShow: Show[Config] = Show.show(_.asJson.noSpaces)
