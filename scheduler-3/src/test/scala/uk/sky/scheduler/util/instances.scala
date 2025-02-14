package uk.sky.scheduler.util

import cats.syntax.all.*
import cats.{Eq, Show}
import fs2.kafka.ProducerRecord
import uk.sky.scheduler.domain.{Metadata, Schedule, ScheduleEvent}
import uk.sky.scheduler.kafka.avro.AvroSchedule

private given Show[AvroSchedule] = Show.show { schedule =>
  s"AvroSchedule(time=${schedule.time}, topic=${schedule.topic}, key=${schedule.key.toList}, value=${schedule.value
      .map(_.toList)}, headers=${schedule.headers.view.mapValues(_.toList).toMap})"
}

private given Show[Metadata] = Show.show { metadata =>
  s"Metadata(id=${metadata.id}, scheduleTopic=${metadata.scheduleTopic})"
}

private given Show[Schedule] = Show.show { schedule =>
  s"ScheduleEvent(time=${schedule.time}, topic=${schedule.topic}, key=${schedule.key.toList}, value=${schedule.value
      .map(_.toList)}, headers=${schedule.headers.view.mapValues(_.toList).toMap})"
}

private given Show[ScheduleEvent] = Show.show { scheduleEvent =>
  s"ScheduleEvent(metadata=${scheduleEvent.metadata.show}, schedule=${scheduleEvent.schedule.show})"
}

private given Eq[AvroSchedule] = Eq.instance((left, right) =>
  left.time === right.time &&
    left.topic === right.topic &&
    left.key.toList === right.key.toList &&
    left.value.map(_.toList) === right.value.map(_.toList) &&
    left.headers.view.mapValues(_.toList).toMap === right.headers.view.mapValues(_.toList).toMap
)

private given Eq[Metadata] = Eq.instance((left, right) =>
  left.id == right.id &&
    left.scheduleTopic === right.scheduleTopic
)

private given Eq[Schedule] = Eq.instance((left, right) =>
  left.time === right.time &&
    left.topic === right.topic &&
    left.key.toList === right.key.toList &&
    left.value.map(_.toList) === right.value.map(_.toList) &&
    left.headers.view.mapValues(_.toList).toMap === right.headers.view.mapValues(_.toList).toMap
)

private given Eq[ScheduleEvent] = Eq.instance((left, right) =>
  left.metadata === right.metadata &&
    left.schedule === right.schedule
)

private given Eq[Array[Byte]]   = Eq.by(_.toList)
private given Show[Array[Byte]] = Show.show(_.mkString("Array(", ", ", ")"))

private given Eq[ProducerRecord[Array[Byte], Option[Array[Byte]]]] = Eq.instance((left, right) =>
  left.topic === right.topic &&
    left.partition === right.partition &&
    left.timestamp === right.timestamp &&
    left.key === right.key &&
    left.value === right.value &&
    left.headers === right.headers
)
