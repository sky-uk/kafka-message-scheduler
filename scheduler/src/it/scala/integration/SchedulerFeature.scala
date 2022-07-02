package integration

import base.IntegrationBase
import com.sky.kms.domain.Schedule.ScheduleWithHeaders
import io.github.embeddedkafka.Codecs.{nullSerializer, stringSerializer}
import io.github.embeddedkafka.EmbeddedKafka.publishToKafka
import utils._

import java.time.OffsetDateTime
import java.util.UUID

class SchedulerFeature extends IntegrationBase {

  Feature("Schedule messages") {
    Scenario("Schedule a message with a value at a specific time") { _ =>
      val messageKey: String                  = UUID.randomUUID().toString
      val in10Seconds                         = OffsetDateTime.now().plusSeconds(10)
      val randomSchedule: ScheduleWithHeaders =
        random[ScheduleWithHeaders].copy(key = messageKey.getBytes, time = in10Seconds, topic = outputTopic)
      println(s"Schedule: $randomSchedule")

      publishToKafka("schedules", messageKey, randomSchedule.toAvro)
      pending
    }

    Scenario("Schedule a message with a delete at a specific time")(_ => pending)

  }
}
