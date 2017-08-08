package com.sky.kms

import java.time.OffsetDateTime

package object domain {

  type ScheduleId = String

  case class Schedule(time: OffsetDateTime, topic: String, key: Array[Byte], value: Array[Byte])

}
