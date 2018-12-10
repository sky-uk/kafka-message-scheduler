package com.sky.kms

import java.time.OffsetDateTime

import scala.concurrent.duration.FiniteDuration

package object domain {

  type ScheduleId = String

  case class Schedule(time: OffsetDateTime, topic: String, key: Array[Byte], value: Option[Array[Byte]])

  case class ScheduleEvent(delay: FiniteDuration, inputTopic: String, outputTopic: String, key: Array[Byte], value: Option[Array[Byte]])

}
