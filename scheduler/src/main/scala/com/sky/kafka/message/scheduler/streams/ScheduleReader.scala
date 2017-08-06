package com.sky.kafka.message.scheduler.streams

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import akka.{Done, NotUsed}
import cats.data.Reader
import com.sky.kafka.message.scheduler.SchedulingActor._
import com.sky.kafka.message.scheduler._
import com.sky.kafka.message.scheduler.config.{AppConfig, SchedulerConfig}
import com.sky.kafka.message.scheduler.domain.ApplicationError
import com.sky.kafka.message.scheduler.kafka._
import com.typesafe.scalalogging.LazyLogging

case class ScheduleReader(config: SchedulerConfig, scheduleSource: Source[DecodeResult, Control], schedulingSink: Sink[Any, NotUsed])
                         (implicit system: ActorSystem, materializer: ActorMaterializer) extends ScheduleReaderStream {

  val stream: Control =
    scheduleSource
      .map(ScheduleReader.toSchedulingMessage)
      .to(PartitionedSink.from(schedulingSink))
      .run()
}

object ScheduleReader extends LazyLogging {

  def toSchedulingMessage(decodeResult: DecodeResult): Either[ApplicationError, SchedulingMessage] =
    decodeResult.map { case (scheduleId, scheduleOpt) =>
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
