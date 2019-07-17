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

      val record =
        ScheduledMessagePublisher.toProducerRecord(schedule.toScheduledMessage)

      record.key() === scheduleId
      Option(record.value()) === schedule.value
      record.topic() shouldBe schedule.outputTopic
    }

    "convert schedule deletion to a delete record" in {
      val List(scheduleId, topic) = random[String](2).toList
      val record = ScheduledMessagePublisher.toProducerRecord(
        ScheduleDeletion(scheduleId, topic, Map.empty)
      )

      record.key() === scheduleId
      record.topic() === topic
      record.value() === null
    }

    "convert scheduled message with an empty value to a delete record" in {
      val schedule =
        random[ScheduleEvent].copy(inputTopic = testTopic, value = None)

      ScheduledMessagePublisher
        .toProducerRecord(schedule.toScheduledMessage)
        .value() === null
    }

    "convert scheduled message with headers to producer record with headers" in {
      val schedule = random[ScheduleEvent]
      val headers = ScheduledMessagePublisher
        .toProducerRecord(schedule.toScheduledMessage)
        .headers()
        .toArray
        .map(header => header.key() -> header.value())

      headers should contain theSameElementsAs schedule.headers
    }
  }
}
