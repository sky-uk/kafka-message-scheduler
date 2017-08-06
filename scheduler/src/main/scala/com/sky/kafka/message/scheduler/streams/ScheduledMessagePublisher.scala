package com.sky.kafka.message.scheduler.streams

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import cats.data.Reader
import com.sky.kafka.message.scheduler.config._
import com.sky.kafka.message.scheduler.domain.ScheduleData._
import org.apache.kafka.clients.producer.ProducerRecord
import com.sky.kafka.message.scheduler.domain._
import com.sky.kafka.message.scheduler.kafka.KafkaStream
import com.sky.kafka.message.scheduler.streams.ScheduledMessagePublisher._

import scala.concurrent.Future

case class ScheduledMessagePublisher(config: SchedulerConfig, publisherSink: Sink[In, Mat])
                                             (implicit system: ActorSystem, materializer: ActorMaterializer)
  extends ScheduledMessagePublisherStream {

  def stream: SourceQueueWithComplete[(ScheduleId, Schedule)] =
    Source.queue[(ScheduleId, Schedule)](config.queueBufferSize, OverflowStrategy.backpressure)
      .mapConcat(splitToScheduleAndMetadata)
      .to(publisherSink)
      .run()

  val splitToScheduleAndMetadata: ((ScheduleId, Schedule)) => List[In] = {
    case (scheduleId, schedule) =>
      List(schedule, ScheduleMetadata(scheduleId, config.scheduleTopic))
  }
}

object ScheduledMessagePublisher {

  type In = ProducerRecord[Array[Byte], Array[Byte]]
  type Mat = Future[Done]

  def reader(implicit system: ActorSystem,
             materializer: ActorMaterializer): Reader[AppConfig, ScheduledMessagePublisher] =
    SchedulerConfig.reader.map(conf => ScheduledMessagePublisher(conf, KafkaStream.sink))
}
