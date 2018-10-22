package com.sky.kms.e2e

import java.util.UUID

import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain._
import net.manub.embeddedkafka.Codecs.{
  stringSerializer,
  stringDeserializer,
  nullSerializer => arrayByteSerializer
}
import net.manub.embeddedkafka.Consumers

class SchedulerDeleteIntSpec extends SchedulerIntSpecBase with Consumers {

  "Scheduler stream" should {

    "schedule a delete message if the body of the scheduled message is None" in withRunningKafka {
      withSchedulerApp {
        val (scheduleId, schedule) =
          (UUID.randomUUID().toString,
           random[ScheduleEvent].copy(value = None).secondsFromNow(4))

        publishToKafka(scheduleTopic, scheduleId, schedule.toAvro)

        val cr = consumeFirstFrom[String](schedule.outputTopic)

        cr.key() should contain theSameElementsInOrderAs schedule.key
        cr.value() shouldBe null
        cr.timestamp() shouldBe schedule.timeInMillis +- Tolerance.toMillis
      }
    }
  }
}
