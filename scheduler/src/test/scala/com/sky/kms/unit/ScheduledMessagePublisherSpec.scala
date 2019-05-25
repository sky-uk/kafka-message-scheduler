package com.sky.kms.unit

import java.util.UUID

import com.sky.kms.base.SpecBase
import com.sky.kms.domain.PublishableMessage.ScheduleDeletion
import com.sky.kms.domain._
import com.sky.kms.streams.ScheduledMessagePublisher
import com.sky.kms.utils.TestDataUtils._

class ScheduledMessagePublisherSpec extends SpecBase {

  val testTopic = UUID.randomUUID().toString

  "toProducerRecord" should {
    "convert scheduled message to producer record" in {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[ScheduleEvent].copy(inputTopic = testTopic))

      val record = ScheduledMessagePublisher.toProducerRecord(schedule.toScheduledMessage)

      record.key() === scheduleId
      record.value() === schedule.value.get
      record.topic() shouldBe schedule.outputTopic
    }

    "convert schedule deletion to a delete record" in {
      val List(scheduleId, topic) = random[String](2).toList
      val record = ScheduledMessagePublisher.toProducerRecord(ScheduleDeletion(scheduleId, topic))

      record.key() === scheduleId
      record.topic() === topic
      record.value() === null
    }

    "convert scheduled message with an empty value to a delete record" in {
      val schedule = random[ScheduleEvent].copy(inputTopic = testTopic, value = None)

      ScheduledMessagePublisher
        .toProducerRecord(schedule.toScheduledMessage)
        .value() === null
    }
  }
}
