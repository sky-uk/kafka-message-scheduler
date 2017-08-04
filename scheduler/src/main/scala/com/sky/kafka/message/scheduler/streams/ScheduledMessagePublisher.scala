package com.sky.kafka.message.scheduler.streams

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import cats.data.Reader
import com.sky.kafka.message.scheduler.domain.{Schedule, ScheduleId, ScheduleMetadata}
import com.sky.kafka.message.scheduler.kafka._
import com.sky.kafka.message.scheduler.{AppConfig, SchedulerConfig, _}

case class ScheduledMessagePublisher(config: SchedulerConfig)
                                       (implicit system: ActorSystem, materializer: ActorMaterializer) extends ScheduledMessagePublisherStream {

  def stream: SourceQueueWithComplete[(ScheduleId, Schedule)] =
    Source.queue[(ScheduleId, Schedule)](config.queueBufferSize, OverflowStrategy.backpressure)
      .mapConcat(splitToScheduleAndMetadata)
      .writeToKafka
      .run()

  val splitToScheduleAndMetadata: ((ScheduleId, Schedule)) => List[Either[ScheduleMetadata, Schedule]] = {
    case (scheduleId, schedule) =>
      List(Right(schedule), Left(ScheduleMetadata(scheduleId, config.scheduleTopic)))
  }
}

object ScheduledMessagePublisher {

  def reader(implicit system: ActorSystem, materializer: ActorMaterializer): Reader[AppConfig, ScheduledMessagePublisher] =
    SchedulerConfig.reader.map(ScheduledMessagePublisher.apply)
}
