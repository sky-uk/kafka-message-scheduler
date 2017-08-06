package com.sky.kafka.message.scheduler.streams

import akka.stream.scaladsl.SourceQueueWithComplete
import cats.Eval
import com.sky.kafka.message.scheduler.config.SchedulerConfig
import com.sky.kafka.message.scheduler.domain.ScheduleData.Schedule
import com.sky.kafka.message.scheduler.domain.ScheduleId
import org.zalando.grafter.{Stop, StopResult}

import scala.concurrent.Await

trait ScheduledMessagePublisherStream extends Stop {

  def config: SchedulerConfig

  def stream: SourceQueueWithComplete[(ScheduleId, Schedule)]

  override def stop: Eval[StopResult] = StopResult.eval("Shutting down queue...") {
    stream.complete()
    Await.result(stream.watchCompletion(), config.shutdownTimeout.stream)
  }

}
