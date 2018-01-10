package com.sky.kms.streams

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import cats.Eval
import com.sky.kms.Start
import com.sky.kms.config._
import com.sky.kms.domain.PublishableMessage._
import com.sky.kms.domain._
import com.sky.kms.kafka.KafkaStream
import com.sky.kms.streams.ScheduledMessagePublisher._
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.Future

/**
  * Provides a stream that consumes from the queue of triggered messages,
  * writes the scheduled messages to the specified Kafka topics and then deletes the schedules
  * from the scheduling Kafka topic to mark completion
  */
case class ScheduledMessagePublisher(config: SchedulerConfig, publisherSink: Sink[SinkIn, SinkMat])
                                    (implicit system: ActorSystem) extends LazyLogging {

  val splitToMessageAndDeletion: (In) => List[SinkIn] = {
    case (scheduleId, scheduledMessage) =>
      logger.info(s"Publishing scheduled message $scheduleId to ${scheduledMessage.topic} and deleting it from ${config.scheduleTopic}")
      List(scheduledMessage, ScheduleDeletion(scheduleId, config.scheduleTopic))
  }

  def stream: RunnableGraph[(Mat, SinkMat)] =
    Source.queue[In](config.queueBufferSize, OverflowStrategy.backpressure)
      .mapConcat(splitToMessageAndDeletion)
      .toMat(publisherSink)(Keep.both)
}

object ScheduledMessagePublisher {

  case class Running(materializedSource: Mat, materializedSink: SinkMat)

  type In = (ScheduleId, ScheduledMessage)
  type Mat = SourceQueueWithComplete[(ScheduleId, ScheduledMessage)]

  type SinkIn = ProducerRecord[Array[Byte], Array[Byte]]
  type SinkMat = Future[Done]

  def configure(implicit system: ActorSystem): Configured[ScheduledMessagePublisher] =
    SchedulerConfig.reader.map(ScheduledMessagePublisher(_, KafkaStream.sink))

  def run(implicit mat: ActorMaterializer): Start[Running] =
    Start(app => Eval.later {
      val (sourceMat, sinkMat) = app.scheduledMessagePublisher.stream.run()
      Running(sourceMat, sinkMat)
    })
}
