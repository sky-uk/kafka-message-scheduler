package com.sky.kms.e2e

import java.util.UUID

import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain._
import org.apache.kafka.common.serialization._

class SchedulerIntSpec extends SchedulerIntBaseSpec {

  "Scheduler stream" should {
    "schedule a message to be sent to Kafka and delete it after it has been emitted" in withRunningSchedulerStream {
      val (scheduleId, schedule) =
        (UUID.randomUUID().toString, random[Schedule].secondsFromNow(4))

      writeToKafka(ScheduleTopic, (scheduleId, schedule.toAvro))

      val cr =
        consumeFromKafka(schedule.topic,
          keyDeserializer = new ByteArrayDeserializer).head

      cr.key() should contain theSameElementsInOrderAs schedule.key
      cr.value() should contain theSameElementsInOrderAs schedule.value.get
      cr.timestamp() shouldBe schedule.timeInMillis +- Tolerance.toMillis

      val latestMessageOnScheduleTopic = consumeLatestFromScheduleTopic

      latestMessageOnScheduleTopic.key() shouldBe scheduleId
      latestMessageOnScheduleTopic.value() shouldBe null
    }
  }

  private def consumeLatestFromScheduleTopic =
    consumeFromKafka(ScheduleTopic, 2, new StringDeserializer).last
}
