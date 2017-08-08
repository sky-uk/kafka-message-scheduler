package com.sky.kms.streams

import akka.stream.scaladsl.SourceQueueWithComplete
import cats.Eval
import com.sky.kms.config.SchedulerConfig
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain.ScheduleId
import com.typesafe.scalalogging.LazyLogging
import org.zalando.grafter.{Stop, StopResult}

import scala.concurrent.Await

trait ScheduledMessagePublisherStream extends Stop with LazyLogging {

  def config: SchedulerConfig

  def stream: SourceQueueWithComplete[(ScheduleId, ScheduledMessage)]

  override def stop: Eval[StopResult] = StopResult.eval("Shutting down queue...") {
    stream.complete()
    Await.result(stream.watchCompletion(), config.shutdownTimeout.stream)
  }

}
