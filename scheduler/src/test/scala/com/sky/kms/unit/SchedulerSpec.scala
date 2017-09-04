package com.sky.kms.unit

import java.time.OffsetDateTime

import com.sky.kms.avro._
import com.sky.kms.base.BaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.consumerRecordDecoder
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import org.apache.kafka.clients.consumer.ConsumerRecord


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
