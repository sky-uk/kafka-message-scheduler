package uk.sky.scheduler.converters

import java.nio.charset.StandardCharsets

import cats.syntax.all.*
import fs2.kafka.*
import io.scalaland.chimney.dsl.*
import uk.sky.scheduler.domain.*
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.message.{Message, Metadata => MessageMetadata}
import uk.sky.scheduler.syntax.all.*

extension (schedule: JsonSchedule) {
  def scheduleEvent(id: String, scheduleTopic: String): ScheduleEvent = {
    val event = schedule
      .into[Schedule]
      .withFieldComputed(_.key, _.key.base64Decode)
      .withFieldComputed(_.value, _.value.map(_.base64Decode))
      .withFieldComputed(_.headers, _.headers.base64DecodeValues)
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
      .withFieldComputed(_.headers, _.headers.base64EncodeValues)
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
    ).withHeaders(Headers(Header(MessageMetadata.expiredKey, MessageMetadata.expiredValue)))
}

extension (cr: ConsumerRecord[String, Either[ScheduleError, Option[JsonSchedule | AvroSchedule]]]) {
  def toMessage: Message[Either[ScheduleError, Option[ScheduleEvent]]] = {
    val key: String   = cr.key
    val topic: String = cr.topic

    val payload: Either[ScheduleError, Option[ScheduleEvent]] = cr.value match {
      case Left(error)        => ScheduleError.DecodeError(key, error.getMessage).asLeft
      case Right(None)        => none[ScheduleEvent].asRight[ScheduleError]
      case Right(Some(input)) =>
        val scheduleEvent = input match {
          case avroSchedule: AvroSchedule => avroSchedule.scheduleEvent(key, topic)
          case jsonSchedule: JsonSchedule => jsonSchedule.scheduleEvent(key, topic)
        }
        scheduleEvent.some.asRight[ScheduleError]
    }

    // TODO - test dropping null keys
    val headers: Map[String, String] =
      cr.headers.toChain.toList.flatMap(header => header.as[Option[String]].map(header.key -> _)).toMap

    Message[Either[ScheduleError, Option[ScheduleEvent]]](
      key = key,
      source = topic,
      value = payload,
      metadata = MessageMetadata.fromMap(headers)
    )
  }
}
