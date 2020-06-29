package com.sky.kms.domain

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.MILLIS

import cats.syntax.either._
import com.sky.kms.domain.ApplicationError.InvalidTimeError

import scala.concurrent.duration._

sealed trait Schedule extends Product with Serializable {

  def getTime: OffsetDateTime = this match {
    case s: Schedule.ScheduleNoHeaders   => s.time
    case s: Schedule.ScheduleWithHeaders => s.time
  }

  def toScheduleEvent(inputKey: String, inputTopic: String): Either[InvalidTimeError, ScheduleEvent] =
    Either
      .catchNonFatal(MILLIS.between(OffsetDateTime.now, getTime).millis)
      .bimap(
        _ => InvalidTimeError(inputKey, getTime),
        delay =>
          this match {
            case Schedule.ScheduleNoHeaders(_, outputTopic, key, value) =>
              ScheduleEvent(delay, inputTopic, outputTopic, key, value, Map.empty)
            case Schedule.ScheduleWithHeaders(_, outputTopic, key, value, headers) =>
              ScheduleEvent(delay, inputTopic, outputTopic, key, value, headers)
        }
      )

}

object Schedule {

  final case class ScheduleNoHeaders(time: OffsetDateTime, topic: String, key: Array[Byte], value: Option[Array[Byte]])
      extends Schedule

  final case class ScheduleWithHeaders(time: OffsetDateTime,
                                       topic: String,
                                       key: Array[Byte],
                                       value: Option[Array[Byte]],
                                       headers: Map[String, Array[Byte]])
      extends Schedule

}
