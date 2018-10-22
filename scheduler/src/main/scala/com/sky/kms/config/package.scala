package com.sky.kms

import cats.data.Reader
import com.sky.kms.kafka.Topic

package object config {

  case class AppConfig(scheduler: SchedulerConfig)

  case class SchedulerConfig(scheduleTopics: Set[Topic],
                             queueBufferSize: Int)

  object SchedulerConfig {
    def configure: Configured[SchedulerConfig] = Reader(_.scheduler)
  }

  type Configured[T] = Reader[AppConfig, T]

}
