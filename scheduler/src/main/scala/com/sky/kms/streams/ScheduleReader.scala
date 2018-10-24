package com.sky.kms.streams

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import cats.instances.either._
import cats.instances.future._
import cats.syntax.functor._
import cats.{Comonad, Eval, Functor, Id, Traverse}
import com.sky.kafka.topicloader._
import com.sky.kms._
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.config._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka._
import com.sky.kms.streams.ScheduleReader.{In, LoadSchedule}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.language.higherKinds

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader[F[_] : Traverse : Comonad, OutMat](loadProcessedSchedules: LoadSchedule => Source[_, _],
                                                             scheduleSource: Eval[Source[F[In], _]],
                                                             schedulingActor: ActorRef,
                                                             commit: Flow[F[Either[ApplicationError, Done]], Done, OutMat],
                                                             errorHandler: Sink[F[Either[ApplicationError, Done]], Future[Done]])(
                                                              implicit f: Functor[F], system: ActorSystem) {

  import system.dispatcher

  val tr = Traverse[F[?]] compose Traverse[Either[ApplicationError, ?]]

  def stream: RunnableGraph[(KillSwitch, Future[Done])] = {
    scheduleSource.value
      .map(f.map(_)(ScheduleReader.toSchedulingMessage))
      .mapAsync(Parallelism)(tr.traverse(_)(msg => (schedulingActor ? msg).mapTo[Ack.type].map[Done](_ => Done)))
      .alsoTo(errorHandler)
      .via(commit)
      .watchTermination() { case (mat, fu) => fu.failed.foreach(schedulingActor ! UpstreamFailure(_)); mat }
      .runAfter(loadProcessedSchedules(msg => (schedulingActor ? msg).mapTo[Ack.type]).watchTermination() { case (_, fu) => fu.foreach(_ => schedulingActor ! Initialised) })
      .toMat(Sink.ignore)(Keep.both)
  }
}

object ScheduleReader extends LazyLogging {

  case class Running[SrcMat, SinkMat](materializedSource: SrcMat, materializedSink: SinkMat)

  type In = Either[ApplicationError, (ScheduleId, Option[ScheduleEvent])]
  type LoadSchedule = SchedulingMessage => Future[Ack.type]

  def toSchedulingMessage(readResult: In): Either[ApplicationError, SchedulingMessage] =
    readResult.map { case (scheduleId, scheduleOpt) =>
      scheduleOpt match {
        case Some(schedule) =>
          CreateOrUpdate(scheduleId, schedule)
        case None =>
          Cancel(scheduleId)
      }
    }

  def configure(actorRef: ActorRef)(implicit system: ActorSystem): Configured[ScheduleReader[Id, NotUsed]] =
    SchedulerConfig.configure.map { config =>
      ScheduleReader[Id, NotUsed](_ => Source.empty, Eval.later(KafkaStream.source(config.scheduleTopics).map(_.value)), actorRef, Flow[Either[ApplicationError, Done]].map(_ => Done), errorHandler)
    }

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Running[KillSwitch, Future[Done]]] =
    Start { app =>
      val (srcMat, sinkMat) = app.reader.stream.run()
      Running(srcMat, sinkMat)
    }
}
