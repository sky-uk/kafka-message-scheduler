package com.sky.kafkamessage.scheduler

import java.util.UUID

import com.sky.kafkamessage.scheduler.config._
import com.sky.kafkamessage.scheduler.domain._
import com.sky.kafkamessage.scheduler.kafka.KafkaStream
import com.sky.kafkamessage.scheduler.streams.ScheduledMessagePublisher
import common.AkkaStreamBaseSpec
import common.TestDataUtils.random
import org.apache.kafka.clients.producer.ProducerRecord
import common.TestDataUtils._

import scala.concurrent.duration._

class ScheduledMessagePublisherSpec extends AkkaStreamBaseSpec {

  val testTopic = UUID.randomUUID().toString
  val publisher = ScheduledMessagePublisher(
    SchedulerConfig(testTopic, ShutdownTimeout(1 second, 1 second), 5),
    KafkaStream.sink
  )

  "splitToScheduleAndMetadata" should {
    "split schedule and convert to producer records" in {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule])
      publisher.splitToScheduleAndMetadata((scheduleId, schedule.toScheduledMessage)) === List(
        new ProducerRecord(schedule.topic, schedule.key, schedule.value),
        new ProducerRecord(testTopic, scheduleId.getBytes, null)
      )
    }
  }
}
