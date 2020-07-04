package com.sky.kms.unit.behaviour

import java.time.OffsetDateTime

import cats.syntax.option._
import com.sky.kms.base.SpecBase
import com.sky.kms.domain.ApplicationError.InvalidTimeError
import com.sky.kms.domain.Schedule
import com.sky.kms.domain.Schedule.ScheduleNoHeaders
import com.sky.kms.domain.{ScheduleEvent, ScheduleId}
import com.sky.kms.streams.ScheduleReader
import com.sky.kms.utils.ScheduleMatcher
import com.sky.kms.utils.TestDataUtils._
import org.apache.kafka.clients.consumer.ConsumerRecord

trait ScheduleDecoderBehaviour { this: SpecBase with ScheduleMatcher =>

  def scheduleDecoder(decode: ConsumerRecord[String, Array[Byte]] => ScheduleReader.In,
                      serialize: Schedule => Array[Byte],
                      legacySerialize: ScheduleNoHeaders => Array[Byte]): Unit = {

    val ScheduleTopic = "scheduleTopic"
    val ScheduleId    = "scheduleId"
    val TestSchedule  = random[ScheduleEvent]

    "decode id and schedule if present" in {
      val someBytes = random[Array[Byte]]
      val schedule  = TestSchedule.copy(value = someBytes.some, inputTopic = ScheduleTopic)

      val cr = artificialConsumerRecord(ScheduleId, serialize(schedule.toSchedule))

      val (id, event) = decode(cr).right.value

      id shouldBe ScheduleId
      event.value should matchScheduleEvent(schedule)
    }

    "be able to decode old schedule events" in {
      val scheduleNoHeaders = random[ScheduleEventNoHeaders]
      val schedule          = scheduleNoHeaders.copy(inputTopic = ScheduleTopic)

      val cr = artificialConsumerRecord(ScheduleId, legacySerialize(schedule.toScheduleWithoutHeaders))

      val (id, event) = decode(cr).right.value

      id shouldBe ScheduleId
      event.value should matchScheduleEvent(schedule.toScheduleEvent)

    }

    "decode id and schedule handling a schedule with null body" in {
      val schedule = TestSchedule.copy(value = None, inputTopic = ScheduleTopic)
      val cr       = artificialConsumerRecord(ScheduleId, serialize(schedule.toSchedule))

      val (id, event) = decode(cr).right.value

      id shouldBe ScheduleId
      event.value should matchScheduleEvent(schedule)
    }

    "decode id without schedule if null value" in {
      val cr = artificialConsumerRecord(ScheduleId, null)

      decode(cr).right.value shouldBe (ScheduleId, None)
    }

    "error if the duration between schedule time and now is beyond the range of FiniteDuration" in {
      val tooDistantFuture = OffsetDateTime.now().plusYears(293)
      val schedule         = TestSchedule.toSchedule.copy(time = tooDistantFuture)
      val cr               = artificialConsumerRecord(ScheduleId, serialize(schedule))

      decode(cr).left.value shouldBe a[InvalidTimeError]
    }
  }

  private def artificialConsumerRecord(scheduleId: ScheduleId, bytes: Array[Byte]) =
    new ConsumerRecord[String, Array[Byte]]("scheduleTopic", 1, 1L, scheduleId, bytes)

}
