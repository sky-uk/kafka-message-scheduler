package com.sky.kms.unit

import java.util.UUID

import cats.Eval
import com.sky.kms.base.AkkaStreamBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.config._
import com.sky.kms.domain._
import com.sky.kms.kafka.KafkaStream
import com.sky.kms.streams.ScheduledMessagePublisher
import org.apache.kafka.clients.producer.ProducerRecord

class ScheduledMessagePublisherSpec extends AkkaStreamBaseSpec {

  val testTopic = UUID.randomUUID().toString
  val publisher = ScheduledMessagePublisher(
    SchedulerConfig(testTopic, 100),
    Eval.now(KafkaStream.sink)
  )

  "splitToMessageAndDeletion" should {
    "split schedule and convert to producer records" in {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule])

      publisher.splitToMessageAndDeletion((scheduleId, schedule.toScheduledMessage)) === List(
        new ProducerRecord(schedule.topic, schedule.key, schedule.value),
        new ProducerRecord(testTopic, scheduleId.getBytes, null)
      )
    }
  }
}
