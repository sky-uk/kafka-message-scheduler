package uk.sky.scheduler.config

import cats.Show
import cats.syntax.all.*
import io.circe.Encoder
import io.circe.generic.semiauto
import io.circe.syntax.*
import monocle.syntax.all.*
import org.apache.kafka.common.config.SslConfigs

import scala.concurrent.duration.FiniteDuration

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

given configFiniteDurationEncoder: Encoder[FiniteDuration] =
  Encoder.instance(fd => s"${fd.length} ${fd.unit.toString.toLowerCase}".asJson)

given schedulerConfigEncoder: Encoder[ScheduleConfig] = semiauto.deriveEncoder[ScheduleConfig]
given configEncoder: Encoder[Config]                  = semiauto.deriveEncoder[Config]

given configShow: Show[Config] = Show.show(_.asJson.noSpaces)
