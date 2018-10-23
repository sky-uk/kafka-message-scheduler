package com.sky.kms.streams

import akka.actor.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Consumer.Control
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl._
import akka.util.Timeout
import akka.{Done, NotUsed}
import cats.data.Nested
import cats.instances.either._
import cats.instances.future._
import cats.syntax.functor._
import cats.syntax.traverse._
import cats.{Eval, Traverse}
import com.sky.kms._
import com.sky.kms.actors.SchedulingActor
import com.sky.kms.actors.SchedulingActor.{Ack, Cancel, CreateOrUpdate, SchedulingMessage}
import com.sky.kms.config._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka._
import com.sky.kms.streams.ScheduleReader.{In, Mat, SinkIn}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.higherKinds

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader[F[_] : Traverse, SrcMat, SinkMat](config: SchedulerConfig, scheduleSource: Eval[Source[F[In], Mat]], schedulingActor: ActorRef, commit: Sink[F[_], SinkMat]) {

  implicit val t = Timeout(5.seconds)

  import scala.concurrent.ExecutionContext.Implicits.global

  val tr = Traverse[F[?]] compose Traverse[Either[ApplicationError, ?]]

  def stream: RunnableGraph[(Mat, SinkMat)] =
    scheduleSource.value
      .map(_.map(ScheduleReader.toSchedulingMessage))
      .mapAsync(5)(tr.traverse(_)(msg => (schedulingActor ? msg).mapTo[Ack.type]))
      .alsoTo(errorSink)
      .toMat(commit)(Keep.both)

  private def sink: Sink[SinkIn, SinkMat] =
    Sink.actorRefWithAck(schedulingActor, SchedulingActor.Init, SchedulingActor.Ack, Done, SchedulingActor.UpstreamFailure)
}

object ScheduleReader extends LazyLogging {

  case class Running(materializedSource: Mat, materializedSink: SinkMat)

  type In = Either[ApplicationError, (ScheduleId, Option[ScheduleEvent])]
  type Mat = Control

  type SinkIn = Any
  type SinkMat = NotUsed

  def toSchedulingMessage(readResult: In): Either[ApplicationError, SchedulingMessage] =
    readResult.map { case (scheduleId, scheduleOpt) =>
      scheduleOpt match {
        case Some(schedule) =>
          CreateOrUpdate(scheduleId, schedule)
        case None =>
          Cancel(scheduleId)
      }
    }

  def configure(actorRef: ActorRef)(implicit system: ActorSystem): Configured[ScheduleReader] =
    for {
      config <- SchedulerConfig.configure
    } yield ScheduleReader(config, Eval.later(KafkaStream.source(config.scheduleTopics).map(_.value)), actorRef)

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Running] =
    Start { app =>
      val (srcMat, sinkMat) = app.reader.stream.run()
      Running(srcMat, sinkMat)
    }
}
