package uk.sky.scheduler.converters

import cats.syntax.all.*
import fs2.kafka.{Header, Headers, ProducerRecord}
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.message.Metadata

import java.nio.charset.StandardCharsets

private trait ScheduleEventConverter {
  extension (scheduleEvent: ScheduleEvent) {
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
      ).withHeaders(Headers(Header(Metadata.expiredKey.toString, Metadata.expiredValue)))
  }
}

object scheduleEvent extends ScheduleEventConverter