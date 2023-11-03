package uk.sky.scheduler

import fs2.kafka.{Header, Headers, ProducerRecord}
import io.scalaland.chimney.dsl.*
import uk.sky.scheduler.domain.Schedule
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.syntax.all.*

case class ScheduleEvent(
    time: Long,
    topic: String,
    key: Array[Byte],
    value: Option[Array[Byte]],
    headers: Map[String, Array[Byte]]
)

object ScheduleEvent {
  def fromSchedule(schedule: Schedule): ScheduleEvent = {
    val key     = schedule.key.base64Decode
    val value   = schedule.value.map(_.base64Decode)
    val headers = schedule.headers.view.mapValues(_.base64Decode).toMap

    ScheduleEvent(
      time = schedule.time,
      topic = schedule.topic,
      key = key,
      value = value,
      headers = headers
    )
  }

  def fromAvroSchedule(avroSchedule: AvroSchedule): ScheduleEvent =
    avroSchedule.transformInto[ScheduleEvent]

  def toSchedule(scheduleEvent: ScheduleEvent): Schedule = {
    val key     = scheduleEvent.key.base64Encode
    val value   = scheduleEvent.value.map(_.base64Encode)
    val headers = scheduleEvent.headers.view.mapValues(_.base64Encode).toMap
    Schedule(
      time = scheduleEvent.time,
      topic = scheduleEvent.topic,
      key = key,
      value = value,
      headers = headers
    )
  }

  def toProducerRecord(scheduleEvent: ScheduleEvent): ProducerRecord[Array[Byte], Option[Array[Byte]]] =
    ProducerRecord(
      topic = scheduleEvent.topic,
      key = scheduleEvent.key,
      value = scheduleEvent.value
    ).withHeaders(Headers.fromSeq(scheduleEvent.headers.toSeq.map(Header.apply)))
}
