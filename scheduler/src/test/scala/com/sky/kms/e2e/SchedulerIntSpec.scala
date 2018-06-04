package com.sky.kms.e2e

import java.util.UUID

import akka.actor.CoordinatedShutdown
import akka.actor.CoordinatedShutdown.UnknownReason
import com.sky.kms.SchedulerApp
import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.config._
import com.sky.kms.domain._
import org.apache.kafka.common.serialization._
import org.scalatest.Assertion

import scala.concurrent.duration._

class SchedulerIntSpec extends SchedulerIntBaseSpec {

  val Tolerance = 200 millis

  "Scheduler stream" should {
    "schedule a message to be sent to Kafka and delete it after it has been emitted" in withRunningSchedulerStream {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule].secondsFromNow(4))

      writeToKafka(ScheduleTopic, (scheduleId, schedule.toAvro))

      val cr = consumeFromKafka(schedule.topic, keyDeserializer = new ByteArrayDeserializer).head

      cr.key() should contain theSameElementsInOrderAs schedule.key
      cr.value() should contain theSameElementsInOrderAs schedule.value
      cr.timestamp() shouldBe schedule.timeInMillis +- Tolerance.toMillis

      val latestMessageOnScheduleTopic = consumeLatestFromScheduleTopic

      latestMessageOnScheduleTopic.key() shouldBe scheduleId
      latestMessageOnScheduleTopic.value() shouldBe null
    }

    "schedule a delete message if the body of the scheduled message is None" in withRunningSchedulerStream {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule].copy(value = None).secondsFromNow(4))

      writeToKafka(ScheduleTopic, (scheduleId, schedule.toAvro))

      val cr = consumeFromKafka(schedule.topic, keyDeserializer = new ByteArrayDeserializer).head

      cr.key() should contain theSameElementsInOrderAs schedule.key
      cr.value() shouldBe null
      cr.timestamp() shouldBe schedule.timeInMillis +- Tolerance.toMillis
    }
  }

  private def withRunningSchedulerStream(scenario: => Assertion) {
    val app = SchedulerApp.configure apply AppConfig(conf)
    SchedulerApp.run apply app

    scenario

    CoordinatedShutdown(system).run(UnknownReason)
  }

  private def consumeLatestFromScheduleTopic =
    consumeFromKafka(ScheduleTopic, 2, new StringDeserializer).last
}
