package com.sky.kafka.message.scheduler.streams

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import cats.data.Reader
import com.sky.kafka.message.scheduler.config._
import com.sky.kafka.message.scheduler.domain.PublishableMessage._
import com.sky.kafka.message.scheduler.domain._
import com.sky.kafka.message.scheduler.kafka.KafkaStream
import com.sky.kafka.message.scheduler.streams.ScheduledMessagePublisher._
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.Future

/**
  * Provides stream from the queue of due messages to the sink
  */
case class ScheduledMessagePublisher(config: SchedulerConfig, publisherSink: Sink[In, Mat])
                                    (implicit system: ActorSystem, materializer: ActorMaterializer)
  extends ScheduledMessagePublisherStream {

  def stream: SourceQueueWithComplete[(ScheduleId, ScheduledMessage)] =
    Source.queue[(ScheduleId, ScheduledMessage)](config.queueBufferSize, OverflowStrategy.backpressure)
      .mapConcat(splitToScheduleAndMetadata)
      .to(publisherSink)
      .run()

  val splitToScheduleAndMetadata: ((ScheduleId, ScheduledMessage)) => List[In] = {
    case (scheduleId, scheduledMessage) =>
      logger.info(s"Publishing scheduled message $scheduleId to ${scheduledMessage.topic} and deleting it from ${config.scheduleTopic}")
      List(scheduledMessage, ScheduleDeletion(scheduleId, config.scheduleTopic))
  }
}

object ScheduledMessagePublisher {

  type In = ProducerRecord[Array[Byte], Array[Byte]]
  type Mat = Future[Done]

  def reader(implicit system: ActorSystem,
             materializer: ActorMaterializer): Reader[AppConfig, ScheduledMessagePublisher] =
    SchedulerConfig.reader.map(conf => ScheduledMessagePublisher(conf, KafkaStream.sink))
}
