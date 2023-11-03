package uk.sky.scheduler.converters

import fs2.kafka.{Header, Headers, ProducerRecord}
import io.scalaland.chimney.dsl.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.syntax.all.*

extension (schedule: JsonSchedule) {
  def scheduleEvent: ScheduleEvent =
    schedule
      .into[ScheduleEvent]
      .withFieldComputed(_.key, _.key.base64Decode)
      .withFieldComputed(_.value, _.value.map(_.base64Decode))
      .withFieldComputed(_.headers, _.headers.view.mapValues(_.base64Decode).toMap)
      .transform

}

extension (schedule: AvroSchedule) {
  def scheduleEvent: ScheduleEvent =
    schedule.transformInto[ScheduleEvent]
}

extension (scheduleEvent: ScheduleEvent) {
  def toJsonSchedule: JsonSchedule =
    scheduleEvent
      .into[JsonSchedule]
      .withFieldComputed(_.key, _.key.base64Encode)
      .withFieldComputed(_.value, _.value.map(_.base64Encode))
      .withFieldComputed(_.headers, _.headers.view.mapValues(_.base64Encode).toMap)
      .transform

  def toAvroSchedule: AvroSchedule =
    scheduleEvent.transformInto[AvroSchedule]

  def toProducerRecord: ProducerRecord[Array[Byte], Option[Array[Byte]]] =
    ProducerRecord(
      topic = scheduleEvent.topic,
      key = scheduleEvent.key,
      value = scheduleEvent.value
    ).withHeaders(Headers.fromSeq(scheduleEvent.headers.toSeq.map(Header.apply)))
}
