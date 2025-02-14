package uk.sky.scheduler.converters

import fs2.kafka.ConsumerRecord
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.message.Message

private trait ConsumerRecordConverter {
  extension (cr: ConsumerRecord[String, Either[ScheduleError, Option[JsonSchedule | AvroSchedule]]]) {
    def toMessage: Message[Either[ScheduleError, Option[ScheduleEvent]]] = ???
  }
}

object consumerRecord extends ConsumerRecordConverter
