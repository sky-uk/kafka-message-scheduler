package com.sky.kafka.message.scheduler

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.Reader

import scala.concurrent.duration.Duration

package object config {

  case class AppConfig(scheduler: SchedulerConfig)(implicit system: ActorSystem, materialzer: ActorMaterializer)

  case class SchedulerConfig(scheduleTopic: String, shutdownTimeout: ShutdownTimeout, queueBufferSize: Int)

  object SchedulerConfig {
    def reader: Reader[AppConfig, SchedulerConfig] = Reader(_.scheduler)
  }

  case class ShutdownTimeout(stream: Duration, system: Duration)

}
