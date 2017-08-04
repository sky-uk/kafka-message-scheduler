package com.sky.kafka.message.scheduler

import java.util.UUID

import com.sky.kafka.message.scheduler.domain.{Schedule, ScheduleMetadata}
import com.sky.kafka.message.scheduler.streams.ScheduledMessagePublisher
import common.AkkaStreamBaseSpec
import common.TestDataUtils.random
import org.apache.kafka.clients.producer.ProducerRecord
import common.TestDataUtils._

import scala.concurrent.duration._

class ScheduledMessagePublisherSpec extends AkkaStreamBaseSpec {

  "splitToScheduleAndMetadata" should {
    "split a (scheduleId, schedule) to a list containing a schedule and a schedule metadata" in {
      val testTopic = UUID.randomUUID().toString
      val publisher = ScheduledMessagePublisher(SchedulerConfig(testTopic, ShutdownTimeout(1 second, 1 second), 5))
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule])
      publisher.splitToScheduleAndMetadata((scheduleId, schedule)) shouldBe List(
        Right(schedule),
        Left(ScheduleMetadata(scheduleId, testTopic))
      )
    }
  }

}
