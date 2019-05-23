package com.sky.kms.unit

import java.util.UUID

import akka.stream.scaladsl.Sink
import cats.Eval
import com.sky.kms.base.AkkaStreamSpecBase
import com.sky.kms.domain._
import com.sky.kms.streams.ScheduledMessagePublisher
import com.sky.kms.utils.TestDataUtils._
import org.apache.kafka.clients.producer.ProducerRecord

class ScheduledMessagePublisherSpec extends AkkaStreamSpecBase {

  val testTopic = UUID.randomUUID().toString
  val publisher = ScheduledMessagePublisher(100, Eval.now(Sink.ignore))

  "splitToMessageAndDeletion" should {
    "split schedule and convert to producer records" in {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[ScheduleEvent].copy(inputTopic = testTopic))

      publisher.splitToMessageAndDeletion((scheduleId, schedule.toScheduledMessage)) === List(
        new ProducerRecord(schedule.outputTopic, schedule.key, schedule.value),
        new ProducerRecord(testTopic, scheduleId.getBytes, null)
      )
    }

    "be able to write a delete to a topic" in {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[ScheduleEvent].copy(inputTopic = testTopic, value = None))

      publisher.splitToMessageAndDeletion((scheduleId, schedule.toScheduledMessage)) === List(
        new ProducerRecord(schedule.outputTopic, schedule.key, null),
        new ProducerRecord(testTopic, scheduleId.getBytes, null)
      )
    }
  }
}
