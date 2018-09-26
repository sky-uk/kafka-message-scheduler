package com.sky.kms.unit

import java.time.{Instant, OffsetDateTime, ZoneOffset}

import cats.syntax.option._
import com.sky.kms.avro._
import com.sky.kms.base.BaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.consumerRecordDecoder
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import org.apache.kafka.clients.consumer.ConsumerRecord


class SchedulerSpec extends BaseSpec {

  val ScheduleId = "scheduleId"
  val TestSchedule = random[ScheduleEvent]

  "consumerRecordDecoder" should {
    "decode id and schedule if present" in {
      val someBytes = random[Array[Byte]]
      val schedule = TestSchedule.copy(value = someBytes.some, inputTopic = "scheduleTopic")
      val cr = artificialConsumerRecord(ScheduleId, schedule.toAvro)

      consumerRecordDecoder(cr) should matchPattern {
        case Right((ScheduleId, Some(ScheduleEvent(schedule.time, schedule.inputTopic, schedule.outputTopic, k, Some(v)))))
          if k === schedule.key && v === schedule.value.get =>
      }
    }

    "decode id and schedule handling a schedule with null body" in {
      val schedule = TestSchedule.copy(value = None, inputTopic = "scheduleTopic")
      val cr = artificialConsumerRecord(ScheduleId, schedule.toAvro)

      consumerRecordDecoder(cr) should matchPattern {
        case Right((ScheduleId, Some(ScheduleEvent(schedule.time, schedule.inputTopic, schedule.outputTopic, k, None))))
          if k === schedule.key && schedule.value === None =>
      }
    }

    "decode id without schedule if null value" in {
      val cr = artificialConsumerRecord(ScheduleId, null)

      consumerRecordDecoder(cr) shouldBe Right((ScheduleId, None))
    }

    "error if message does not adhere to our schema" in {
      val cr = new ConsumerRecord[String, Array[Byte]]("scheduleTopic", 1, 1l, ScheduleId, Array.emptyByteArray)

      consumerRecordDecoder(cr) shouldBe Left(InvalidSchemaError(ScheduleId))
    }

  }

  private def artificialConsumerRecord(scheduleId: ScheduleId, avroBytes: Array[Byte]) = {
    new ConsumerRecord[String, Array[Byte]]("scheduleTopic", 1, 1l, scheduleId, avroBytes)
  }

}
