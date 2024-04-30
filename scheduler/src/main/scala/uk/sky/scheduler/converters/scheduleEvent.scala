package uk.sky.scheduler.converters

import java.nio.charset.StandardCharsets

import cats.syntax.all.*
import fs2.kafka.{Header, Headers, ProducerRecord}
import io.scalaland.chimney.dsl.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.message.Metadata

private trait ScheduleEventConverter {
  import base64.given

  extension (scheduleEvent: ScheduleEvent) {
    def toJsonSchedule: JsonSchedule =
      scheduleEvent.schedule.transformInto[JsonSchedule]

    def toAvroSchedule: AvroSchedule =
      scheduleEvent.schedule.transformInto[AvroSchedule]

    def toProducerRecord: ProducerRecord[Array[Byte], Option[Array[Byte]]] =
      ProducerRecord(
        topic = scheduleEvent.schedule.topic,
        key = scheduleEvent.schedule.key,
        value = scheduleEvent.schedule.value
      ).withHeaders(Headers.fromIterable(scheduleEvent.schedule.headers.map(Header.apply)))

    def toTombstone: ProducerRecord[Array[Byte], Option[Array[Byte]]] =
      ProducerRecord(
        topic = scheduleEvent.metadata.scheduleTopic,
        key = scheduleEvent.metadata.id.getBytes(StandardCharsets.UTF_8),
        value = none[Array[Byte]]
      ).withHeaders(Headers(Header(Metadata.expiredKey, Metadata.expiredValue)))
  }
}

object scheduleEvent extends ScheduleEventConverter
