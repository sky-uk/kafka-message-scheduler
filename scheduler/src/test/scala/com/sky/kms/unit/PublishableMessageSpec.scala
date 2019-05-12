package com.sky.kms.unit

import com.sky.kms.base.SpecBase
import com.sky.kms.domain.{PublishableMessage, ScheduleEvent}
import com.sky.kms.utils.TestDataUtils._
import org.apache.kafka.clients.producer.ProducerRecord

class PublishableMessageSpec extends SpecBase {

  "scheduledMessageProducerRecordEnc" should {
    "write a message" in {
      val schedule = random[ScheduleEvent].copy(value = Some("cupcat".getBytes))

      PublishableMessage.scheduledMessageProducerRecordEnc(
        schedule.toScheduledMessage) shouldBe new ProducerRecord(schedule.outputTopic,
                                                                 schedule.key,
                                                                 schedule.value.get)
    }

    "write a Schedule with a value of None as null" in {
      val schedule = random[ScheduleEvent].copy(value = None)

      PublishableMessage.scheduledMessageProducerRecordEnc(
        schedule.toScheduledMessage) shouldBe new ProducerRecord(schedule.outputTopic,
        schedule.key,
        null)
    }
  }
}
