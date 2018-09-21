package com.sky.kms

import java.time.OffsetDateTime

package object domain {

  type ScheduleId = String

  case class AvroSchedule(time: OffsetDateTime, topic: String, key: Array[Byte], value: Option[Array[Byte]])

  case class Schedule(time: OffsetDateTime, inputTopic: String, outputTopic: String, key: Array[Byte], value: Option[Array[Byte]])

}
