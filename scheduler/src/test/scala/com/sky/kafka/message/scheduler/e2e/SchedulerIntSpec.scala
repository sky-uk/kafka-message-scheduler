package com.sky.kafka.message.scheduler.e2e

import java.time.OffsetDateTime
import java.util.UUID

import com.sky.kafka.message.scheduler.domain.Schedule
import com.sky.kafka.message.scheduler.{SchedulerConfig, SchedulerStream, ShutdownTimeout}
import com.sky.kafka.message.scheduler.avro._
import common.TestDataUtils._
import common.{AkkaStreamBaseSpec, KafkaIntSpec}
import org.apache.kafka.common.serialization._
import org.scalactic.TripleEqualsSupport.Spread
import org.scalatest.Assertion

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.math.Numeric.LongIsIntegral

class SchedulerIntSpec extends AkkaStreamBaseSpec with KafkaIntSpec {

  val ScheduleTopic = "scheduleTopic"

  val conf = SchedulerConfig(ScheduleTopic, ShutdownTimeout(10 seconds, 10 seconds))

  val tolerance = 200 millis

  "Scheduler stream" should {
    "schedule a message to be sent to Kafka and delete it after it has been emitted" in withRunningSchedulerStream {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule].secondsFromNow(2))

      writeToKafka(ScheduleTopic, scheduleId, schedule.toAvro)

      val cr = consumeFromKafka(schedule.topic, keyDeserializer = new ByteArrayDeserializer).head

      cr.key() should contain theSameElementsInOrderAs schedule.key
      cr.value() should contain theSameElementsInOrderAs schedule.value
      cr.timestamp() shouldBe schedule.timeInMillis +- tolerance.toMillis

      val latestMessageOnScheduleTopic = consumeLatestFromScheduleTopic

      latestMessageOnScheduleTopic.key() shouldBe scheduleId
      latestMessageOnScheduleTopic.value() shouldBe null
    }
  }

  private def withRunningSchedulerStream(scenario: => Assertion) {
    val stream = SchedulerStream(conf).run

    scenario

    Await.result(stream.shutdown, 5 seconds)
  }

  private def consumeLatestFromScheduleTopic = consumeFromKafka(ScheduleTopic, 2, new StringDeserializer).last
}
