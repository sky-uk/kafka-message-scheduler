package com.sky.kafka.message.scheduler

import java.util.UUID

import com.sky.kafka.message.scheduler.domain.Schedule
import common.AkkaStreamBaseSpec
import common.TestDataUtils.random
import org.apache.kafka.clients.producer.ProducerRecord
import common.TestDataUtils._

class ScheduledMessagePublisherSpec extends AkkaStreamBaseSpec {

  "flow" should {
    "send a producer record for the scheduled the message and one with a null payload" in {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule])
      ???
    }
  }

  def createProducerRecord[K](topic: String, key: K, v: Option[Array[Byte]]): ProducerRecord[K, Array[Byte]] =
    new ProducerRecord[K, Array[Byte]](topic, key, v.orNull)
}
