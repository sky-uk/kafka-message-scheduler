package com.sky.kms.streams

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import cats.data.Reader
import com.sky.kms.config._
import com.sky.kms.domain.PublishableMessage._
import com.sky.kms.domain._
import com.sky.kms.kafka.KafkaStream
import com.sky.kms.streams.ScheduledMessagePublisher._
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.Future

/**
  * Provides a stream that consumes from the queue of triggered messages,
  * writes the messages to Kafka and then deletes the schedules from Kafka
  */
case class ScheduledMessagePublisher(config: SchedulerConfig, publisherSink: Sink[In, Mat])
                                    (implicit system: ActorSystem, materializer: ActorMaterializer)
  extends ScheduledMessagePublisherStream {

  def stream: SourceQueueWithComplete[(ScheduleId, ScheduledMessage)] =
    Source.queue[(ScheduleId, ScheduledMessage)](config.queueBufferSize, OverflowStrategy.backpressure)
      .mapConcat(splitToMessageAndDeletion)
      .to(publisherSink)
      .run()

  val splitToMessageAndDeletion: ((ScheduleId, ScheduledMessage)) => List[In] = {
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
