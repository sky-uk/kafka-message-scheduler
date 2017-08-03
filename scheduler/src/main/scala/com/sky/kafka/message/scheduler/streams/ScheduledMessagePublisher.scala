package com.sky.kafka.message.scheduler.streams

import akka.actor.ActorSystem
import akka.stream.scaladsl.{RunnableGraph, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import cats.data.Reader
import com.sky.kafka.message.scheduler._
import com.sky.kafka.message.scheduler.domain.{Schedule, ScheduleId}
import com.sky.kafka.message.scheduler.kafka.{ProducerRecordEncoder, _}
import com.sky.kafka.message.scheduler.{AppConfig, SchedulerConfig}

case class ScheduledMessagePublisher(schedulerConfig: SchedulerConfig)
                                       (implicit system: ActorSystem) {

  def stream[T: ProducerRecordEncoder]: RunnableGraph[SourceQueueWithComplete[(ScheduleId, Schedule)]] =
    Source.queue[(ScheduleId, Schedule)](schedulerConfig.queueBufferSize, OverflowStrategy.backpressure)
      .map { case (scheduleId, schedule) => schedule } //TODO: implement this flow
      .writeToKafka
}

object ScheduledMessagePublisher {

  def reader(implicit system: ActorSystem): Reader[AppConfig, ScheduledMessagePublisher] =
    SchedulerConfig.reader.map(ScheduledMessagePublisher.apply)
}
