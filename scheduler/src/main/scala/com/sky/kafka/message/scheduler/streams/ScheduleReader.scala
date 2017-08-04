package com.sky.kafka.message.scheduler.streams

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, SourceQueueWithComplete}
import cats.data.Reader
import com.sky.kafka.message.scheduler.SchedulingActor._
import com.sky.kafka.message.scheduler._
import com.sky.kafka.message.scheduler.domain.ScheduleId
import com.sky.kafka.message.scheduler.kafka._
import com.typesafe.scalalogging.LazyLogging

case class ScheduleReader(config: SchedulerConfig, queue: SourceQueueWithComplete[(ScheduleId, domain.Schedule)])
                         (implicit system: ActorSystem, materializer: ActorMaterializer) extends SchedulerReaderStream {

  val schedulingActorRef = system.actorOf(SchedulingActor.props(queue, system.scheduler))

  val stream: Control =
    consumeFromKafka(config.scheduleTopic)
      .map(ScheduleReader.toSchedulingMessage)
      .to(Sink.actorRefWithAck(schedulingActorRef, SchedulingActor.Init, Ack, Done))
      .run()
}

object ScheduleReader extends LazyLogging {

  val toSchedulingMessage: DecodeScheduleResult => SchedulingMessage = {
    case Right((scheduleId, Some(schedule))) =>
      logger.info(s"Publishing scheduled message with ID: $scheduleId to topic: ${schedule.topic}")
      CreateOrUpdate(scheduleId, schedule)
    case Right((scheduleId, None)) =>
      logger.info(s"Cancelling schedule $scheduleId")
      Cancel(scheduleId)
    // match not exhaustive as pending error handling
  }

  def reader(implicit system: ActorSystem, materializer: ActorMaterializer): Reader[AppConfig, ScheduleReader] =
    for {
      conf <- SchedulerConfig.reader
      publisher <- ScheduledMessagePublisher.reader
    } yield ScheduleReader(conf, publisher.stream)
}
