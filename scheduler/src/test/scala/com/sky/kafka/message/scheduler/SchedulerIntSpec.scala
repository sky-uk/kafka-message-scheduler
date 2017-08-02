package com.sky.kafka.message.scheduler

import common.TestDataUtils._
import com.sky.kafka.message.scheduler.domain.Schedule
import common.{AkkaStreamBaseSpec, KafkaIntSpec}
import org.apache.kafka.common.serialization._
import org.scalatest.Assertion

import scala.concurrent.Await
import scala.concurrent.duration._
import avro._

class SchedulerIntSpec extends AkkaStreamBaseSpec with KafkaIntSpec {

  val ScheduleTopic = "scheduleTopic"

  val conf = SchedulerConfig(ScheduleTopic, ShutdownTimeout(10 seconds, 10 seconds))

  "Scheduler stream" should {
    "schedule a message to be sent to Kafka" in withRunningSchedulerStream {
      val schedule = random[Schedule]

      writeToKafka(ScheduleTopic, "scheduleId", schedule.toAvro)

      val (consumedKey, consumedValue) =
        consumeFromKafka(schedule.topic, keyDeserializer = new ByteArrayDeserializer).head

      consumedKey.get should contain theSameElementsInOrderAs schedule.key
      consumedValue should contain theSameElementsInOrderAs schedule.value
    }

    "publish a delete to the schedule topic after emitting scheduled message" ignore withRunningSchedulerStream {
      val schedule = random[Schedule]

      writeToKafka(ScheduleTopic, "scheduleId", schedule.toAvro)

      val records = consumeFromKafka(ScheduleTopic, 2, new StringDeserializer)
      records.size shouldBe 2

      val (consumedKey, consumedValue) = records.last
      consumedKey.get shouldBe "scheduleId"
      consumedValue shouldBe null
    }
  }

  private def withRunningSchedulerStream(scenario: => Assertion) {
    val stream = SchedulerStream(conf).run

    scenario

    Await.result(stream.shutdown, 5 seconds)
  }

}
