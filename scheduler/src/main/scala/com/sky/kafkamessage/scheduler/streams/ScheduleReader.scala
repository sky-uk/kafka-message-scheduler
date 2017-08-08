package com.sky.kafkamessage.scheduler.streams

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import akka.{Done, NotUsed}
import cats.data.Reader
import com.sky.kafkamessage.scheduler.SchedulingActor._
import com.sky.kafkamessage.scheduler._
import com.sky.kafkamessage.scheduler.config.{AppConfig, SchedulerConfig}
import com.sky.kafkamessage.scheduler.domain.{ApplicationError, Schedule, ScheduleId}
import com.sky.kafkamessage.scheduler.kafka._
import com.typesafe.scalalogging.LazyLogging

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader(config: SchedulerConfig,
                          scheduleSource: Source[Either[ApplicationError, (ScheduleId, Option[Schedule])], Control],
                          schedulingSink: Sink[Any, NotUsed])
                         (implicit system: ActorSystem, materializer: ActorMaterializer) extends ScheduleReaderStream {

  val stream: Control =
    scheduleSource
      .map(ScheduleReader.toSchedulingMessage)
      .to(PartitionedSink.from(schedulingSink))
      .run()
}

object ScheduleReader extends LazyLogging {

  def toSchedulingMessage[T](either: Either[ApplicationError, (ScheduleId, Option[Schedule])]): Either[ApplicationError, SchedulingMessage] =
    either.map { case (scheduleId, scheduleOpt) =>
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
