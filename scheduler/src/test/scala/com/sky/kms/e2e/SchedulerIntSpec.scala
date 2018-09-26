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
      val (scheduleId1, schedule1) =
        (UUID.randomUUID().toString, random[ScheduleEvent].secondsFromNow(4))
      val (scheduleId2, schedule2) =
        (UUID.randomUUID().toString, random[ScheduleEvent].secondsFromNow(4))

      val topic1 = ScheduleTopic.toSeq(0)
      val topic2 = ScheduleTopic.toSeq(1)

      writeToKafka(topic1, (scheduleId1, schedule1.toAvro))
      writeToKafka(topic2, (scheduleId2, schedule2.toAvro))

      assertScheduleInOutputTopic(schedule1)
      assertScheduleInOutputTopic(schedule2)

      assertScheduleNulledFromInputTopic(scheduleId1, topic1)
      assertScheduleNulledFromInputTopic(scheduleId2, topic2)
    }
  }

  private def assertScheduleNulledFromInputTopic(scheduleId: ScheduleId, topic: String) = {
    val latestMessageOnScheduleTopic = consumeFromKafka(topic, 2, new StringDeserializer).last

    latestMessageOnScheduleTopic.key() shouldBe scheduleId
    latestMessageOnScheduleTopic.value() shouldBe null
  }

  private def assertScheduleInOutputTopic(schedule: ScheduleEvent) = {
    val cr =
      consumeFromKafka(schedule.outputTopic,
        keyDeserializer = new ByteArrayDeserializer).head

    cr.key() should contain theSameElementsInOrderAs schedule.key
    cr.value() should contain theSameElementsInOrderAs schedule.value.get
    cr.timestamp() shouldBe schedule.timeInMillis +- Tolerance.toMillis
  }
}
