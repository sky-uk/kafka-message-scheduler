package com.sky.kms.e2e

import java.util.UUID

import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain._
import org.apache.kafka.common.serialization._

import scala.concurrent.duration._

class SchedulerDeleteIntSpec extends SchedulerIntBaseSpec {

  val Tolerance = 200 millis

  "Scheduler stream" should {

    "schedule a delete message if the body of the scheduled message is None" in withRunningSchedulerStream {
      val (scheduleId, schedule) =
        (UUID.randomUUID().toString,
         random[Schedule].copy(value = None).secondsFromNow(4))

      writeToKafka(ScheduleTopic, (scheduleId, schedule.toAvro))

      val cr = consumeFromKafka(schedule.topic, keyDeserializer = new ByteArrayDeserializer).head

      cr.key() should contain theSameElementsInOrderAs schedule.key
      cr.value() shouldBe null
      cr.timestamp() shouldBe schedule.timeInMillis +- Tolerance.toMillis
    }
  }
}
