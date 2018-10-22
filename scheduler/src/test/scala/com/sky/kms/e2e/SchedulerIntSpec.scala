package com.sky.kms.e2e

import java.util.UUID

import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain._
import net.manub.embeddedkafka.Codecs._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.scalatest.Assertion

class SchedulerIntSpec extends SchedulerIntSpecBase {

  "Scheduler stream" should {
    "schedule a message to be sent to Kafka and delete it after it has been emitted" in withSchedulerApp {
      val (scheduleId1, schedule1) =
        (UUID.randomUUID().toString, random[ScheduleEvent].secondsFromNow(4))
      val (scheduleId2, schedule2) =
        (UUID.randomUUID().toString, random[ScheduleEvent].secondsFromNow(4))

      withRunningKafka {
        publishToKafka(scheduleTopic, scheduleId1, schedule1.toAvro)
        publishToKafka(extraScheduleTopic, scheduleId2, schedule2.toAvro)

        assertScheduledMsgHasBeenWritten(schedule1)
        assertScheduledMsgHasBeenWritten(schedule2)

        assertScheduleTombstoned(scheduleId1, scheduleTopic)
        assertScheduleTombstoned(scheduleId2, extraScheduleTopic)
      }
    }
  }

  private def assertScheduleTombstoned(scheduleId: ScheduleId,
                                       topic: String) = {
    val latestMessageOnScheduleTopic: ConsumerRecord[String, String] =
      consumeSomeFrom[String](topic, 2).last

    latestMessageOnScheduleTopic.key() shouldBe scheduleId
    latestMessageOnScheduleTopic.value() shouldBe null
  }

  private def assertScheduledMsgHasBeenWritten(schedule: ScheduleEvent): Assertion = {
    val cr = consumeFirstFrom[Array[Byte]](schedule.outputTopic)

    cr.key() should contain theSameElementsInOrderAs schedule.key
    cr.value() should contain theSameElementsInOrderAs schedule.value.get
    cr.timestamp() shouldBe schedule.timeInMillis +- Tolerance.toMillis
  }
}
