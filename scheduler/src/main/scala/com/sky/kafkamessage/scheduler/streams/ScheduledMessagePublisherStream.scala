package com.sky.kafkamessage.scheduler.streams

import akka.stream.scaladsl.SourceQueueWithComplete
import cats.Eval
import com.sky.kafkamessage.scheduler.config.SchedulerConfig
import com.sky.kafkamessage.scheduler.domain.PublishableMessage.ScheduledMessage
import com.sky.kafkamessage.scheduler.domain.ScheduleId
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
