package com.sky.kafka.message.scheduler.streams

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{RunnableGraph, Sink}
import cats.data.Reader
import com.sky.kafka.message.scheduler.SchedulingActor.{Ack, CreateOrUpdate, SchedulingMessage}
import com.sky.kafka.message.scheduler.kafka._
import com.sky.kafka.message.scheduler.{AkkaComponents, AppConfig, SchedulerConfig, SchedulerInput, SchedulingActor}
import com.typesafe.scalalogging.LazyLogging

case class ScheduleReader(config: SchedulerConfig, publisherStream: ScheduledMessagePublisher)
                         (implicit system: ActorSystem, mat: ActorMaterializer) {

  val queue = publisherStream.stream.run()

  val schedulingActorRef = system.actorOf(SchedulingActor.props(queue, system.scheduler))

  def stream: RunnableGraph[Control] =
    consumeFromKafka(config.scheduleTopic)
      .map(ScheduleReader.toSchedulingMessage)
      .to(Sink.actorRefWithAck(schedulingActorRef, SchedulingActor.Init, Ack, Done))

}

object ScheduleReader extends LazyLogging with AkkaComponents {

  val toSchedulingMessage: SchedulerInput => SchedulingMessage = {
    case Right((scheduleId, Some(schedule))) =>
      logger.info(s"Publishing scheduled message with ID: $scheduleId to topic: ${schedule.topic}")
      CreateOrUpdate(scheduleId, schedule)
    // match not exhaustive as pending error handling
  }

  def reader: Reader[AppConfig, ScheduleReader] =
    for {
      conf <- SchedulerConfig.reader
      publisher <- ScheduledMessagePublisher.reader
    } yield ScheduleReader(conf, publisher)
}
