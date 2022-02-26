package com.sky.kms.domain

import java.time.OffsetDateTime
import scala.concurrent.duration.FiniteDuration

sealed trait Schedule extends Product with Serializable

object Schedule {

  final case class ScheduleNoHeaders(time: OffsetDateTime, topic: String, key: Array[Byte], value: Option[Array[Byte]])
      extends Schedule

  final case class ScheduleWithHeaders(
      time: OffsetDateTime,
      topic: String,
      key: Array[Byte],
      value: Option[Array[Byte]],
      headers: Map[String, Array[Byte]]
  ) extends Schedule

  implicit class ScheduleOps(val s: Schedule) extends AnyVal {
    def getTime                                                    = Schedule.getTime(s)
    def toScheduleEvent(delay: FiniteDuration, inputTopic: String) = Schedule.toScheduleEvent(delay, inputTopic, s)
  }

  private def getTime(schedule: Schedule): OffsetDateTime = schedule match {
    case ScheduleNoHeaders(time, _, _, _)      => time
    case ScheduleWithHeaders(time, _, _, _, _) => time
  }

  private def toScheduleEvent(delay: FiniteDuration, inputTopic: String, schedule: Schedule): ScheduleEvent =
    schedule match {
      case ScheduleNoHeaders(_, outputTopic, key, value)            =>
        ScheduleEvent(delay, inputTopic, outputTopic, key, value, Map.empty)
      case ScheduleWithHeaders(_, outputTopic, key, value, headers) =>
        ScheduleEvent(delay, inputTopic, outputTopic, key, value, headers)
    }
}
