package com.sky.kafka.message.scheduler

import java.time.OffsetDateTime

import common.TestDataUtils._
import com.sky.kafka.message.scheduler.domain._
import common.BaseSpec
import org.apache.kafka.clients.consumer.ConsumerRecord
import avro._
import com.sky.kafka.message.scheduler.domain.ApplicationError._
import com.sky.kafka.message.scheduler.domain.ScheduleData.Schedule

class SchedulerSpec extends BaseSpec {

  val ScheduleId = "scheduleId"
  val TestSchedule = random[Schedule]

  "consumerRecordDecoder" should {
    "decode id and schedule if present" in {
      val cr = artificialConsumerRecord(ScheduleId, TestSchedule.toAvro)

      consumerRecordDecoder(cr) should matchPattern {
        case Right((ScheduleId, Some(Schedule(TestSchedule.time, TestSchedule.topic, k, v))))
          if k === TestSchedule.key && v === TestSchedule.value =>
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

    "error if it fails to decode" in {
      val cr = artificialConsumerRecord(ScheduleId, wrongAvro)

      consumerRecordDecoder(cr) should matchPattern {
        case Left(AvroMessageFormatError(ScheduleId, t)) if t.getMessage.contains("love") =>
      }
    }
  }

  private def artificialConsumerRecord(scheduleId: ScheduleId, avroBytes: Array[Byte]) = {
    new ConsumerRecord[String, Array[Byte]]("scheduleTopic", 1, 1l, scheduleId, avroBytes)
  }

  private lazy val wrongAvro: Array[Byte] = {
    implicit val offsetDateTimeToValue = new com.sksamuel.avro4s.ToValue[OffsetDateTime] {
      override def apply(value: OffsetDateTime): String = "we love avro"
    }
    TestSchedule.toAvro
  }
}
