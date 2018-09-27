package com.sky.kms

import cats.data.Reader

import scala.concurrent.duration.FiniteDuration

package object config {

  case class AppConfig(scheduler: SchedulerConfig)

  case class SchedulerConfig(scheduleTopic: Set[String], queueBufferSize: Int)

  object SchedulerConfig {
    def configure: Configured[SchedulerConfig] = Reader(_.scheduler)
  }

  type Configured[T] = Reader[AppConfig, T]

}
