package com.sky.kms.streams

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import cats.data.Reader
import com.sky.kms.SchedulingActor._
import com.sky.kms._
import com.sky.kms.config.{SchedulerConfig, _}
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka._
import com.sky.kms.streams.ScheduleReader.{In, Mat, SinkIn, SinkMat}
import com.typesafe.scalalogging.LazyLogging

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader(config: SchedulerConfig, scheduleSource: Source[In, Mat]) {

  def stream(sink: Sink[SinkIn, SinkMat]): RunnableGraph[Mat] =
    scheduleSource
      .map(ScheduleReader.toSchedulingMessage)
      .to(PartitionedSink.withRight(sink))
}

object ScheduleReader extends LazyLogging {

  type In = Either[ApplicationError, (ScheduleId, Option[Schedule])]
  type Mat = Control

  type SinkIn = Any
  type SinkMat = NotUsed

  def toSchedulingMessage[T](readResult: In): Either[ApplicationError, SchedulingMessage] =
    readResult.map { case (scheduleId, scheduleOpt) =>
      scheduleOpt match {
        case Some(schedule) =>
          logger.info(s"Publishing scheduled message with ID: $scheduleId to topic: ${schedule.topic}")
          CreateOrUpdate(scheduleId, schedule)
        case None =>
          logger.info(s"Cancelling schedule $scheduleId")
          Cancel(scheduleId)
      }
    }

  def configure(implicit system: ActorSystem): Configured[ScheduleReader] =
    SchedulerConfig.reader.map(config => ScheduleReader(config, KafkaStream.beginningOffsetSource(config)))

  def run(queue: ScheduledMessagePublisher.Mat)(implicit system: ActorSystem, mat: ActorMaterializer): Start[Mat] = {
    val actorRef = system.actorOf(SchedulingActor.props(queue))
    val actorSink = Sink.actorRefWithAck(actorRef, SchedulingActor.Init, Ack, Done)
    Reader(_.scheduleReader.stream(actorSink).run())
  }

  def stop: Stop[Done] =
    Stop(_.runningReader.shutdown())
}
