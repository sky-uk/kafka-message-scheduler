package uk.sky.scheduler.converters

import java.nio.charset.StandardCharsets

import cats.syntax.all.*
import fs2.kafka.{Header, Headers, ProducerRecord}
import io.scalaland.chimney.dsl.*
import uk.sky.scheduler.Message
import uk.sky.scheduler.domain.*
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.syntax.all.*

extension (schedule: JsonSchedule) {
  def scheduleEvent(id: String, scheduleTopic: String): ScheduleEvent = {
    val event = schedule
      .into[Schedule]
      .withFieldComputed(_.key, _.key.base64Decode)
      .withFieldComputed(_.value, _.value.map(_.base64Decode))
      .withFieldComputed(_.headers, _.headers.view.mapValues(_.base64Decode).toMap)
      .transform

    ScheduleEvent(Metadata(id, scheduleTopic), event)
  }

}

extension (schedule: AvroSchedule) {
  def scheduleEvent(id: String, scheduleTopic: String): ScheduleEvent = {
    val event = schedule.transformInto[Schedule]

    ScheduleEvent(Metadata(id, scheduleTopic), event)
  }
}

extension (scheduleEvent: ScheduleEvent) {
  def toJsonSchedule: JsonSchedule =
    scheduleEvent.schedule
      .into[JsonSchedule]
      .withFieldComputed(_.key, _.key.base64Encode)
      .withFieldComputed(_.value, _.value.map(_.base64Encode))
      .withFieldComputed(_.headers, _.headers.view.mapValues(_.base64Encode).toMap)
      .transform

  def toAvroSchedule: AvroSchedule =
    scheduleEvent.schedule.transformInto[AvroSchedule]

  def toProducerRecord: ProducerRecord[Array[Byte], Option[Array[Byte]]] =
    ProducerRecord(
      topic = scheduleEvent.schedule.topic,
      key = scheduleEvent.schedule.key,
      value = scheduleEvent.schedule.value
    ).withHeaders(Headers.fromSeq(scheduleEvent.schedule.headers.toSeq.map(Header.apply)))

  def toTombstone: ProducerRecord[Array[Byte], Option[Array[Byte]]] =
    ProducerRecord(
      topic = scheduleEvent.metadata.scheduleTopic,
      key = scheduleEvent.metadata.id.getBytes(StandardCharsets.UTF_8),
      value = none[Array[Byte]]
    ).withHeaders(Headers(Header(Message.expiredHeader, "true")))
}
