package com.sky.kms

import akka.util.Timeout
import cats.data.{NonEmptyList, Reader}
import com.sky.kms.BackoffRestartStrategy.Restarts
import com.sky.kms.kafka.Topic
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive

import scala.concurrent.duration._

package object config {

  implicit val t = Timeout(5.seconds)

  val Parallelism = 10

  case class AppConfig(scheduler: SchedulerConfig)

  case class SchedulerConfig(scheduleTopics: NonEmptyList[Topic], queueBufferSize: Int, topicLoader: LoaderConfig)

  case class LoaderConfig(idleTimeout: FiniteDuration, bufferSize: Int Refined Positive, parallelism: Int Refined Positive)

  object SchedulerConfig {
    def configure: Configured[SchedulerConfig] = Reader(_.scheduler)
  }

  type Configured[T] = Reader[AppConfig, T]

  val appRestartStrategy = BackoffRestartStrategy(1.minute, maxBackoff = 5.minutes, Restarts(20))

}
