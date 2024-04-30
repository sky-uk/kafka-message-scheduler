package uk.sky.scheduler.converters

import cats.syntax.all.*
import fs2.kafka.ConsumerRecord
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import uk.sky.scheduler.domain.{Metadata, Schedule, ScheduleEvent}
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.message.{Message, Metadata as MessageMetadata}

private trait ConsumerRecordConverter {
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
            case avroSchedule: AvroSchedule =>
              avroSchedule.into[Schedule].withFieldComputed(_.headers, _.headers.getOrElse(Map.empty)).transform
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
