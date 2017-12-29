package com.sky.kms.streams

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import com.sky.kms.SchedulingActor._
import com.sky.kms._
import com.sky.kms.config._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka._
import com.sky.kms.streams.ScheduleReader.{In, Mat, SinkIn, SinkMat}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContextExecutor
import scala.util.Failure
import scala.util.control.NonFatal

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader(config: SchedulerConfig, scheduleSource: Source[In, Mat]) extends LazyLogging {

  def stream(sink: Sink[SinkIn, SinkMat])(implicit system: ActorSystem, materializer: Materializer) = {
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val (control, (result, _)) = scheduleSource
      .map(ScheduleReader.toSchedulingMessage)
      .toMat(PartitionedSink.withRight(sink))(Keep.both).run()

    result.onComplete {
      case Failure(NonFatal(e)) =>
        logger.error("Failure during consumption, killing application", e)
        control.shutdown() onComplete (_ => System.exit(1))
    }

    control
  }
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
    SchedulerConfig.reader.map(config => ScheduleReader(config, KafkaStream.source(config)))

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Mat] =
    for {
      queue <- ScheduledMessagePublisher.run
      actorRef = system.actorOf(SchedulingActor.props(queue))
      actorSink = Sink.actorRefWithAck(actorRef, SchedulingActor.Init, Ack, Done)
      running <- Start(_.scheduleReader.stream(actorSink))
    } yield running

  def stop: Stop[Done] =
    Stop(_.runningReader.shutdown())
}
