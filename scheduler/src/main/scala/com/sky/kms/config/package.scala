package com.sky.kms

import cats.data.Reader

import scala.concurrent.duration.Duration

package object config {

  case class AppConfig(scheduler: SchedulerConfig)

  case class SchedulerConfig(scheduleTopic: String, shutdownTimeout: ShutdownTimeout, queueBufferSize: Int)

  object SchedulerConfig {
    def reader: Reader[AppConfig, SchedulerConfig] = Reader(_.scheduler)
  }

  case class ShutdownTimeout(stream: Duration, system: Duration)

}
