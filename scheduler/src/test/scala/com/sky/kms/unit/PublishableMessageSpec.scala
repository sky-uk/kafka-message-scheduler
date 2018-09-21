package com.sky.kms.unit

import java.util.UUID

import com.sky.kms.base.BaseSpec
import com.sky.kms.domain.{PublishableMessage, Schedule}
import com.sky.kms.common.TestDataUtils._
import org.apache.kafka.clients.producer.ProducerRecord

class PublishableMessageSpec extends BaseSpec {

  "scheduledMessageProducerRecordEnc" should {
    "write a message" in {
      val (scheduleId, schedule) =
        (UUID.randomUUID().toString,
         random[Schedule].copy(value = Some("cupcat".getBytes)))

      PublishableMessage.scheduledMessageProducerRecordEnc(
        schedule.toScheduledMessage) shouldBe new ProducerRecord(schedule.outputTopic,
                                                                 schedule.key,
                                                                 schedule.value.get)
    }

    "write a Schedule with a value of None as null" in {
      val (_, schedule) = (UUID.randomUUID().toString, random[Schedule].copy(value = None))

      PublishableMessage.scheduledMessageProducerRecordEnc(
        schedule.toScheduledMessage) shouldBe new ProducerRecord(schedule.outputTopic,
        schedule.key,
        null)
    }
  }
}
