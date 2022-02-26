package com.sky.kms.streams

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import cats.Eval
import com.sky.kms.Start
import com.sky.kms.actors.PublisherActor.ScheduleQueue
import com.sky.kms.config._
import com.sky.kms.domain.PublishableMessage._
import com.sky.kms.domain._
import com.sky.kms.streams.ScheduledMessagePublisher._
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer

import scala.concurrent.Future

/** Provides a stream that consumes from the queue of triggered messages, writes the scheduled messages to the specified
  * Kafka topics and then deletes the schedules from the scheduling Kafka topic to mark completion
  */
case class ScheduledMessagePublisher(queueBufferSize: Int, publisherSink: Eval[Sink[SinkIn, SinkMat]])
    extends LazyLogging {

  def stream: RunnableGraph[(Mat, SinkMat)] =
    Source
      .queue[In](queueBufferSize, OverflowStrategy.backpressure)
      .mapConcat(splitToMessageAndDeletion)
      .toMat(publisherSink.value)(Keep.both)

  val splitToMessageAndDeletion: In => List[PublishableMessage] = { case (scheduleId, scheduledMessage) =>
    logger.info(
      s"Publishing scheduled message $scheduleId to ${scheduledMessage.outputTopic} and deleting it from ${scheduledMessage.inputTopic}"
    )
    List(scheduledMessage, ScheduleDeletion(scheduleId, scheduledMessage.inputTopic, scheduledMessage.headers))
  }
}

object ScheduledMessagePublisher {

  case class Running(materializedSource: Mat, materializedSink: SinkMat)

  type In  = (ScheduleId, ScheduledMessage)
  type Mat = ScheduleQueue

  type SinkIn  = PublishableMessage
  type SinkMat = Future[Done]

  def configure(implicit system: ActorSystem): Configured[ScheduledMessagePublisher] = {

    val writeMsgToKafka = Eval.always(
      Flow[PublishableMessage]
        .map(toProducerRecord)
        .toMat(Producer.plainSink(ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer)))(
          Keep.right
        )
    )

    PublisherConfig.configure.map(c => ScheduledMessagePublisher(c.queueBufferSize, writeMsgToKafka))
  }

  val toProducerRecord: PublishableMessage => ProducerRecord[Array[Byte], Array[Byte]] = {
    case ScheduledMessage(_, outputTopic, key, value, headers) =>
      new ProducerRecord(outputTopic, null, key, value.orNull, headers.asKafkaHeaders)
    case ScheduleDeletion(id, outputTopic, headers)            =>
      new ProducerRecord(outputTopic, null, id.getBytes, null, headers.asKafkaHeaders)
  }

  def run(implicit system: ActorSystem): Start[Running] =
    Start { app =>
      val (sourceMat, sinkMat) = app.publisher.stream.run()
      Running(sourceMat, sinkMat)
    }
}
