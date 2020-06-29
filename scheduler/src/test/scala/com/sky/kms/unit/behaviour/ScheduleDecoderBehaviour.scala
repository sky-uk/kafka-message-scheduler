package com.sky.kms.unit.behaviour

import java.time.OffsetDateTime

import cats.syntax.option._
import com.sky.kms.base.SpecBase
import com.sky.kms.domain.ApplicationError.InvalidTimeError
import com.sky.kms.domain.Schedule.ScheduleWithHeaders
import com.sky.kms.domain.{ScheduleEvent, ScheduleId}
import com.sky.kms.streams.ScheduleReader
import com.sky.kms.utils.TestDataUtils._
import org.apache.kafka.clients.consumer.ConsumerRecord

trait ScheduleDecoderBehaviour { this: SpecBase =>

  def scheduleDecoder(decode: ConsumerRecord[String, Array[Byte]] => ScheduleReader.In,
                      serialize: ScheduleWithHeaders => Array[Byte]): Unit = {

    val ScheduleTopic = "scheduleTopic"
    val ScheduleId    = "scheduleId"
    val TestSchedule  = random[ScheduleEvent]

    "decode id and schedule if present" in {
      val someBytes = random[Array[Byte]]
      val schedule  = TestSchedule.copy(value = someBytes.some, inputTopic = ScheduleTopic)

      val cr = artificialConsumerRecord(ScheduleId, serialize(schedule.toSchedule))

      decode(cr) should matchPattern {
        case Right((ScheduleId, Some(ScheduleEvent(_, schedule.inputTopic, schedule.outputTopic, k, Some(v), headers))))
            if k === schedule.key && v === someBytes && equalHeaders(headers, schedule.headers) =>
      }
    }

    "decode id and schedule handling a schedule with null body" in {
      val schedule = TestSchedule.copy(value = None, inputTopic = ScheduleTopic)
      val cr       = artificialConsumerRecord(ScheduleId, serialize(schedule.toSchedule))

      decode(cr) should matchPattern {
        case Right((ScheduleId, Some(ScheduleEvent(_, schedule.inputTopic, schedule.outputTopic, k, None, headers))))
            if k === schedule.key && schedule.value === None && equalHeaders(headers, schedule.headers) =>
      }
    }

    "decode id without schedule if null value" in {
      val cr = artificialConsumerRecord(ScheduleId, null)

      decode(cr) shouldBe Right((ScheduleId, None))
    }

    "error if the duration between schedule time and now is beyond the range of FiniteDuration" in {
      val tooDistantFuture = OffsetDateTime.now().plusYears(293)
      val schedule         = TestSchedule.toSchedule.copy(time = tooDistantFuture)
      val cr               = artificialConsumerRecord(ScheduleId, serialize(schedule))

      decode(cr).left.get shouldBe a[InvalidTimeError]
    }
  }

  private def artificialConsumerRecord(scheduleId: ScheduleId, bytes: Array[Byte]) =
    new ConsumerRecord[String, Array[Byte]]("scheduleTopic", 1, 1L, scheduleId, bytes)

  private def equalHeaders(x: Map[String, Array[Byte]], y: Map[String, Array[Byte]]): Boolean =
    x.mapValues(_.toList) === y.mapValues(_.toList)

}
