package com.sky.kms.streams

import akka.kafka.scaladsl.Consumer.Control
import cats.Eval
import com.sky.kms.config.SchedulerConfig
import org.zalando.grafter.{Stop, StopResult}

import scala.concurrent.Await

trait ScheduleReaderStream extends Stop {

  def config: SchedulerConfig

  def stream: Control

  override def stop: Eval[StopResult] = StopResult.eval("Shutting down reader stream...")(
    Await.result(stream.shutdown(), config.shutdownTimeout.stream)
  )

}
