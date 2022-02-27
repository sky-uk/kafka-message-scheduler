package com.sky.kms.unit

import java.time.OffsetDateTime

import cats.syntax.option._
import com.sky.kms.base.SpecBase
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.scheduleConsumerRecordDecoder
import com.sky.kms.utils.TestDataUtils._
import org.apache.kafka.clients.consumer.ConsumerRecord

class SchedulerSpec extends SpecBase {

  val ScheduleId   = "scheduleId"
  val TestSchedule = random[ScheduleEvent]

  "scheduleConsumerRecordDecoder" should {
    "decode id and schedule if present" in {
      val someBytes = random[Array[Byte]]
      val schedule  = TestSchedule.copy(value = someBytes.some, inputTopic = "scheduleTopic")
      val cr        = artificialConsumerRecord(ScheduleId, schedule.toSchedule.toAvro)

      scheduleConsumerRecordDecoder(cr) should matchPattern {
        case Right((ScheduleId, Some(ScheduleEvent(_, schedule.inputTopic, schedule.outputTopic, k, Some(v), headers))))
            if k === schedule.key && v === someBytes && equalHeaders(headers, schedule.headers) =>
      }
    }

    "decode id and schedule handling a schedule with null body" in {
      val schedule = TestSchedule.copy(value = None, inputTopic = "scheduleTopic")
      val cr       = artificialConsumerRecord(ScheduleId, schedule.toSchedule.toAvro)

      scheduleConsumerRecordDecoder(cr) should matchPattern {
        case Right((ScheduleId, Some(ScheduleEvent(_, schedule.inputTopic, schedule.outputTopic, k, None, headers))))
            if k === schedule.key && schedule.value === None && equalHeaders(headers, schedule.headers) =>
      }
    }

    "decode id without schedule if null value" in {
      val cr = artificialConsumerRecord(ScheduleId, null)

      scheduleConsumerRecordDecoder(cr) shouldBe Right((ScheduleId, None))
    }

    "error if message does not adhere to our schema" in {
      val cr = new ConsumerRecord[String, Array[Byte]]("scheduleTopic", 1, 1L, ScheduleId, Array.emptyByteArray)

      scheduleConsumerRecordDecoder(cr) shouldBe Left(InvalidSchemaError(ScheduleId))
    }

    "error if the duration between schedule time and now is beyond the range of FiniteDuration" in {
      val tooDistantFuture = OffsetDateTime.now().plusYears(293)
      val schedule         = TestSchedule.toSchedule.copy(time = tooDistantFuture)
      val cr               = artificialConsumerRecord(ScheduleId, schedule.toAvro)

      scheduleConsumerRecordDecoder(cr).left.toOption.get shouldBe a[InvalidTimeError]
    }
  }

  private def artificialConsumerRecord(scheduleId: ScheduleId, avroBytes: Array[Byte]) =
    new ConsumerRecord[String, Array[Byte]]("scheduleTopic", 1, 1L, scheduleId, avroBytes)

  private def equalHeaders(x: Map[String, Array[Byte]], y: Map[String, Array[Byte]]): Boolean =
    x.view.mapValues(_.toList).toMap === y.view.mapValues(_.toList).toMap
}
