package com.sky.kafka.message.scheduler

import java.time.OffsetDateTime

package object domain {

  type ScheduleId = String

  case class Schedule(time: OffsetDateTime, topic: String, key: Array[Byte], value: Array[Byte])

}
