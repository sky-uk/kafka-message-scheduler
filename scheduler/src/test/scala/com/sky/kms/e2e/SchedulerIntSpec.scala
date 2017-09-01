package com.sky.kms.e2e

import java.util.UUID

import com.sky.kms.SchedulerApp
import com.sky.kms.avro._
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.common.{AkkaStreamBaseSpec, KafkaIntSpec}
import com.sky.kms.config._
import com.sky.kms.domain._
import org.apache.kafka.common.serialization._
import org.scalatest.Assertion
import com.sky.kms.AkkaComponents._

import scala.concurrent.duration._

class SchedulerIntSpec extends AkkaStreamBaseSpec with KafkaIntSpec {

  val ScheduleTopic = "scheduleTopic"

  implicit val shutdownTimeout = ShutdownTimeout(10 seconds, 10 seconds)

  val conf = AppConfig(SchedulerConfig(ScheduleTopic, shutdownTimeout, 100))

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
    val app = SchedulerApp.reader akka conf
    val runningApp = SchedulerApp.run akka app

    scenario
    SchedulerApp.stop akka runningApp
  }

  private def consumeLatestFromScheduleTopic =
    consumeFromKafka(ScheduleTopic, 2, new StringDeserializer).last
}
