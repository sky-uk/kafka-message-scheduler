package com.sky.kms.streams

import akka.actor.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Consumer.Control
import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import cats.Eval
import com.sky.kms._
import com.sky.kms.actors.SchedulingActor
import com.sky.kms.actors.SchedulingActor.{Cancel, CreateOrUpdate, SchedulingMessage}
import com.sky.kms.config._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka._
import com.sky.kms.streams.ScheduleReader.{In, Mat, SinkIn, SinkMat}
import com.typesafe.scalalogging.LazyLogging

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader(config: SchedulerConfig, scheduleSource: Eval[Source[In, Mat]], schedulingActor: ActorRef) {

  def stream: RunnableGraph[(Mat, SinkMat)] =
    scheduleSource.value
      .map(ScheduleReader.toSchedulingMessage)
      .toMat(PartitionedSink.withRight(sink).mapMaterializedValue(_._2))(Keep.both)

  private def sink: Sink[SinkIn, SinkMat] =
    Sink.actorRefWithAck(schedulingActor, SchedulingActor.Init, SchedulingActor.Ack, Done, SchedulingActor.UpstreamFailure)
}

object ScheduleReader extends LazyLogging {

  case class Running(materializedSource: Mat, materializedSink: SinkMat)

  type In = Either[ApplicationError, (ScheduleId, Option[Schedule])]
  type Mat = Control

  type SinkIn = Any
  type SinkMat = NotUsed

  def toSchedulingMessage(readResult: In): Either[ApplicationError, SchedulingMessage] =
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

  def configure(actorRef: ActorRef)(implicit system: ActorSystem): Configured[ScheduleReader] =
    for {
      config <- SchedulerConfig.configure
    } yield ScheduleReader(config, Eval.later(KafkaStream.source(config)), actorRef)

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Running] =
    Start { app =>
      val (srcMat, sinkMat) = app.reader.stream.run()
      Running(srcMat, sinkMat)
    }
}
