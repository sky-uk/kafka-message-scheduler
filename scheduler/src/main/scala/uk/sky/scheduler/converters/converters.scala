package uk.sky.scheduler.converters

import java.nio.charset.StandardCharsets
import java.util.Base64

import cats.syntax.all.*
import fs2.kafka.*
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import uk.sky.scheduler.domain.*
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.message.{Message, Metadata as MessageMetadata}

object all extends Base64Converter, ScheduleEventConverter, ConsumerRecordConverter

private trait Base64Converter {
  private val b64Decoder = Base64.getDecoder
  private val b64Encoder = Base64.getEncoder

  private[converters] given b64EncodeTransformer: Transformer[Array[Byte], String] =
    (src: Array[Byte]) => src.base64Encode

  private[converters] given b64DecodeTransformer: Transformer[String, Array[Byte]] =
    (src: String) => src.base64Decode

  extension (s: String) {
    def base64Decode: Array[Byte] = b64Decoder.decode(s.getBytes(StandardCharsets.UTF_8))

    def base64Encode: String = s.getBytes(StandardCharsets.UTF_8).base64Encode
  }

  extension (bytes: Array[Byte]) {
    def base64Encode: String = b64Encoder.encodeToString(bytes)
  }
}

object base64 extends Base64Converter

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
      ).withHeaders(Headers(Header(MessageMetadata.expiredKey, MessageMetadata.expiredValue)))
  }
}

object scheduleEvent extends ScheduleEventConverter

private trait ConsumerRecordConverter {
  import base64.given

  extension (cr: ConsumerRecord[String, Either[ScheduleError, Option[JsonSchedule | AvroSchedule]]]) {
    def toMessage: Message[Either[ScheduleError, Option[ScheduleEvent]]] = {
      val key: String   = cr.key
      val topic: String = cr.topic

      val payload: Either[ScheduleError, Option[ScheduleEvent]] = cr.value match {
        case Left(error) => ScheduleError.DecodeError(key, error.getMessage).asLeft[Option[ScheduleEvent]]

        case Right(None) => none[ScheduleEvent].asRight[ScheduleError]

        case Right(Some(input)) =>
          val metadata = Metadata(key, topic)

          val schedule = input match {
            case avroSchedule: AvroSchedule => avroSchedule.transformInto[Schedule]
            case jsonSchedule: JsonSchedule => jsonSchedule.transformInto[Schedule]
          }

          ScheduleEvent(metadata, schedule).some.asRight[ScheduleError]
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
}

object consumerRecord extends ConsumerRecordConverter
