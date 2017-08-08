package com.sky.kms.streams

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import akka.{Done, NotUsed}
import cats.data.Reader
import com.sky.kms.SchedulingActor._
import com.sky.kms._
import com.sky.kms.config.{AppConfig, SchedulerConfig}
import com.sky.kms.domain._
import com.sky.kms.kafka._
import com.sky.kms.streams.ScheduleReader.{In, Mat}
import com.typesafe.scalalogging.LazyLogging

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader(config: SchedulerConfig,
                          scheduleSource: Source[In, Mat],
                          schedulingSink: Sink[Any, NotUsed])
                         (implicit system: ActorSystem, materializer: ActorMaterializer) extends ScheduleReaderStream {

  val stream: Mat =
    scheduleSource
      .map(ScheduleReader.toSchedulingMessage)
      .to(PartitionedSink.withRight(schedulingSink))
      .run()
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

  def reader(implicit system: ActorSystem, materializer: ActorMaterializer): Reader[AppConfig, ScheduleReader] =
    for {
      config <- SchedulerConfig.reader
      schedulingActorRef <- SchedulingActor.reader
    } yield ScheduleReader(
      config = config,
      scheduleSource = KafkaStream.source(config),
      schedulingSink = Sink.actorRefWithAck(schedulingActorRef, SchedulingActor.Init, Ack, Done)
    )
}
