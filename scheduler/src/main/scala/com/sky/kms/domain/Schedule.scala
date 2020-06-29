package com.sky.kms.domain

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.MILLIS

import cats.syntax.either._
import com.sky.kms.domain.ApplicationError.InvalidTimeError

import scala.concurrent.duration._

final case class Schedule(time: OffsetDateTime,
                          topic: String,
                          key: Array[Byte],
                          value: Option[Array[Byte]],
                          headers: Map[String, Array[Byte]] = Map.empty) {

  def toScheduleEvent(inputKey: String, inputTopic: String): Either[InvalidTimeError, ScheduleEvent] =
    Either
      .catchNonFatal(MILLIS.between(OffsetDateTime.now, time).millis)
      .bimap(
        _ => InvalidTimeError(inputKey, time),
        delay => ScheduleEvent(delay, inputTopic, topic, key, value, headers)
      )

}

object Schedule {

  final case class ScheduleNoHeaders(time: OffsetDateTime,
                                     topic: String,
                                     key: Array[Byte],
                                     value: Option[Array[Byte]]) {
    val toSchedule: Schedule = Schedule(time, topic, key, value, Map.empty)
  }

}
