package com.sky.kms.streams

import akka.{Done, NotUsed}
import akka.actor.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Consumer.Control
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl._
import cats.instances.either._
import cats.instances.future._
import cats.syntax.functor._
import cats.{Comonad, Eval, Functor, Id, Traverse}
import com.sky.kms._
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.config._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka._
import com.sky.kms.streams.ScheduleReader.In
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.language.higherKinds

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader[F[_] : Traverse : Comonad, InMat, OutMat](scheduleSource: Eval[Source[F[In], InMat]],
                                                                    schedulingActor: ActorRef,
                                                                    commit: Flow[F[Either[ApplicationError, Done]], Done, OutMat],
                                                                    errorHandler: Sink[F[Either[ApplicationError, Done]], Future[Done]])(
                                                                     implicit f: Functor[F], system: ActorSystem) {

  import system.dispatcher

  val tr = Traverse[F[?]] compose Traverse[Either[ApplicationError, ?]]

  schedulingActor ! Init

  def stream: RunnableGraph[(InMat, Future[Done])] = {
    scheduleSource.value
      .map(f.map(_)(ScheduleReader.toSchedulingMessage))
      .mapAsync(Parallelism)(tr.traverse(_)(msg => (schedulingActor ? msg).mapTo[Ack.type].map[Done](_ => Done)))
      .alsoTo(errorHandler)
      .via(commit)
      .watchTermination() { case (mat, fu) => fu.failed.foreach(schedulingActor ! UpstreamFailure(_)); mat }
      .toMat(Sink.ignore)(Keep.both)
  }
}

object ScheduleReader extends LazyLogging {

  case class Running[SrcMat, SinkMat](materializedSource: SrcMat, materializedSink: SinkMat)

  type In = Either[ApplicationError, (ScheduleId, Option[ScheduleEvent])]
  type KafkaReader = ScheduleReader[KafkaMessage, Control, Future[Done]]

  def toSchedulingMessage(readResult: In): Either[ApplicationError, SchedulingMessage] =
    readResult.map { case (scheduleId, scheduleOpt) =>
      scheduleOpt match {
        case Some(schedule) =>
          CreateOrUpdate(scheduleId, schedule)
        case None =>
          Cancel(scheduleId)
      }
    }

  def configure(actorRef: ActorRef)(implicit system: ActorSystem): Configured[ScheduleReader[Id, Control, NotUsed]] =
    SchedulerConfig.configure.map { config =>
      ScheduleReader[Id, Control, NotUsed](Eval.later(KafkaStream.source(config.scheduleTopics).map(_.value)), actorRef, Flow[Either[ApplicationError, Done]].map(_ => Done), errorHandler)
    }

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Running[Control, Future[Done]]] =
    Start { app =>
      val (srcMat, sinkMat) = app.reader.stream.run()
      Running(srcMat, sinkMat)
    }
}
