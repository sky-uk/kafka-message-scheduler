package com.sky.kafka.message.scheduler.domain

import java.time.OffsetDateTime

import com.sky.kafka.message.scheduler.kafka.ProducerRecordEncoder
import org.apache.kafka.clients.producer.ProducerRecord

sealed trait ScheduleData

object ScheduleData {

  case class Schedule(time: OffsetDateTime, topic: String, key: Array[Byte], value: Array[Byte]) extends ScheduleData

  case class ScheduleMetadata(scheduleId: ScheduleId, topic: String) extends ScheduleData

  implicit val scheduleProducerRecordEncoder: ProducerRecordEncoder[Schedule] =
    ProducerRecordEncoder.instance(schedule => new ProducerRecord(schedule.topic, schedule.key, schedule.value))

  implicit val scheduleMedatadataProducerRecordEncoder: ProducerRecordEncoder[ScheduleMetadata] =
    ProducerRecordEncoder.instance(metadata => new ProducerRecord(metadata.topic, metadata.scheduleId.getBytes, null))


  implicit def scheduleDataToProducerRecord(scheduleData: ScheduleData): ProducerRecord[Array[Byte], Array[Byte]] = scheduleData match {
    case schedule: Schedule => scheduleProducerRecordEncoder(schedule)
    case metadata: ScheduleMetadata => scheduleMedatadataProducerRecordEncoder(metadata)
  }
}
