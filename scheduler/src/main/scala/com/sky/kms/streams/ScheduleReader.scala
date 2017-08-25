package com.sky.kms.streams

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream._
import akka.stream.scaladsl.{RunnableGraph, Sink, Source}
import akka.{Done, NotUsed}
import cats.data.Reader
import com.sky.kms.SchedulerApp.RunningSchedulerApp
import com.sky.kms.SchedulingActor._
import com.sky.kms._
import com.sky.kms.config.{AppConfig, SchedulerConfig, ShutdownTimeout}
import com.sky.kms.domain._
import com.sky.kms.kafka._
import com.sky.kms.streams.ScheduleReader.{In, Mat}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Await

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader(config: SchedulerConfig,
                          scheduleSource: Source[In, Mat]) {

  val stream: Reader[Sink[Any, NotUsed], RunnableGraph[Mat]] =
    Reader(sink =>
      scheduleSource
        .map(ScheduleReader.toSchedulingMessage)
        .to(PartitionedSink.withRight(sink))
    )
}

object ScheduleReader extends LazyLogging {

  type In = Either[ApplicationError, (ScheduleId, Option[Schedule])]

  type Mat = Control

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

  def reader(implicit system: ActorSystem): Reader[AppConfig, ScheduleReader] =
    SchedulerConfig.reader.map(config => ScheduleReader(config, KafkaStream.source(config)))

  def runner(implicit system: ActorSystem, mat: ActorMaterializer): Reader[SchedulerApp, Mat] =
    ScheduledMessagePublisher.runner.flatMap { queue =>
      val actorRef = system.actorOf(SchedulingActor.props(queue))
      Reader(_.scheduleReader.stream(Sink.actorRefWithAck(actorRef, SchedulingActor.Init, Ack, Done)).run())
    }

  def stop(implicit timeout: ShutdownTimeout): Reader[RunningSchedulerApp, Unit] =
    Reader(app => Await.ready(app.runningReader.shutdown(), timeout.stream))
}
