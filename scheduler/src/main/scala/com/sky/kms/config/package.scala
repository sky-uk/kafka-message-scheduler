package com.sky.kms

import cats.data.Reader

import scala.concurrent.duration.Duration

package object config {

  case class AppConfig(scheduler: SchedulerConfig)

  case class SchedulerConfig(scheduleTopic: String, shutdownTimeout: Duration, queueBufferSize: Int)

  object SchedulerConfig {
    def reader: Reader[AppConfig, SchedulerConfig] = Reader(_.scheduler)
  }

  type Configured[T] = Reader[AppConfig, T]

}
