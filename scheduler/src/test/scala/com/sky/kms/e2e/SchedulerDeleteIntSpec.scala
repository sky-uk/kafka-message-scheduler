package com.sky.kms.e2e

import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.utils.TestDataUtils._
import com.sky.kms.domain._
import eu.timepit.refined.auto._
import net.manub.embeddedkafka.Codecs.{nullSerializer => arrayByteSerializer, stringDeserializer, stringSerializer}

class SchedulerDeleteIntSpec extends SchedulerIntSpecBase {

  "Scheduler stream" should {
    "schedule a delete message if the value of the scheduled message is empty" in withRunningKafka {
      withSchedulerApp {
        val scheduleId = random[String]
        val schedule   = random[ScheduleEvent].copy(value = None).secondsFromNow(4).toSchedule

        publishToKafka(scheduleTopic, scheduleId, schedule.toAvro)

        val cr = consumeFirstFrom[String](schedule.topic)

        cr.key should contain theSameElementsInOrderAs schedule.key
        cr.value shouldBe null
        cr.timestamp shouldBe schedule.timeInMillis +- tolerance.toMillis
      }
    }
  }
}
