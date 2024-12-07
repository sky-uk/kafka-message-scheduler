package uk.sky.scheduler.converters

import cats.syntax.all.*
import fs2.kafka.ConsumerRecord
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import org.typelevel.ci.CIString
import uk.sky.scheduler.domain.{Metadata, Schedule, ScheduleEvent}
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.message.{Message, Metadata as MessageMetadata}

import scala.util.chaining.*

private trait ConsumerRecordConverter {
  extension (cr: ConsumerRecord[String, Either[ScheduleError, Option[JsonSchedule | AvroSchedule]]]) {
    def toMessage: Message[Either[ScheduleError, Option[ScheduleEvent]]] = {
      val key: String   = cr.key
      val topic: String = cr.topic

      val payload: Either[ScheduleError, Option[ScheduleEvent]] = cr.value match {
        case Left(error) => ScheduleError.DecodeError(key, error).asLeft[Option[ScheduleEvent]]

        case Right(None) => none[ScheduleEvent].asRight[ScheduleError]

        case Right(Some(input)) =>
          val metadata = Metadata(key, topic)

          val schedule = input match {
            case avroSchedule: AvroSchedule => avroSchedule.transformInto[Schedule]
            case jsonSchedule: JsonSchedule => jsonSchedule.transformInto[Schedule]
          }

          ScheduleEvent(metadata, schedule).some.asRight[ScheduleError]
      }

      val metadata: MessageMetadata =
        cr.headers.toChain.toList.view
          .map(header => header.key -> header.as[Option[String]])
          .collect { case (key, Some(value)) => CIString(key) -> value }
          .pipe(MessageMetadata.apply)

      Message[Either[ScheduleError, Option[ScheduleEvent]]](
        key = key,
        source = topic,
        value = payload,
        metadata = metadata
      )
    }
  }
}

object consumerRecord extends ConsumerRecordConverter
