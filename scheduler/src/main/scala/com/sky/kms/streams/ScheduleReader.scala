package com.sky.kms.streams

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import cats.instances.either._
import cats.instances.future._
import cats.syntax.functor._
import cats.syntax.option._
import cats.{Comonad, Eval, Functor, Traverse}
import com.sky.kafka.topicloader._
import com.sky.kms.Restartable._
import com.sky.kms._
import com.sky.kms.actors.SchedulingActor
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.config._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka._
import com.sky.kms.streams.ScheduleReader.{In, LoadSchedule}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.concurrent.Future
import scala.language.higherKinds

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader[F[_] : Traverse : Comonad](loadProcessedSchedules: LoadSchedule => Source[_, _],
                                                     scheduleSource: Eval[Source[F[In], _]],
                                                     schedulingActor: ActorRef,
                                                     commit: Flow[F[Either[ApplicationError, Ack.type]], Done, NotUsed],
                                                     errorHandler: Sink[F[Either[ApplicationError, Ack.type]], Future[Done]],
                                                     restartStrategy: BackoffRestartStrategy)(
                                                      implicit f: Functor[F], system: ActorSystem) {

  import system.dispatcher

  val tr = Traverse[F[?]] compose Traverse[Either[ApplicationError, ?]]

  def stream: Source[Done, KillSwitch] =
    scheduleSource.value
      .map(f.map(_)(ScheduleReader.toSchedulingMessage))
      .mapAsync(Parallelism)(tr.traverse(_)(processSchedulingMessage))
      .alsoTo(errorHandler)
      .via(commit)
      .restartUsing(restartStrategy)
      .watchTermination() { case (mat, fu) => fu.failed.foreach(schedulingActor ! UpstreamFailure(_)); mat }
      .runAfter(loadProcessedSchedules(processSchedulingMessage).watchTermination() { case (_, fu) => fu.foreach(_ => schedulingActor ! Initialised) })

  private def processSchedulingMessage(msg: SchedulingMessage): Future[SchedulingActor.Ack.type] =
    (schedulingActor ? msg).mapTo[Ack.type]
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

  def configure(actorRef: ActorRef)(implicit system: ActorSystem): Configured[ScheduleReader[KafkaMessage]] =
    SchedulerConfig.configure.map { config =>
      def reloadSchedules(loadSchedule: LoadSchedule) = {
        import system.dispatcher
        import config.topicLoader._
        val f = (cr: ConsumerRecord[String, Array[Byte]]) => toSchedulingMessage(scheduleConsumerRecordDecoder(cr))
        val tlc = TopicLoaderConfig(LoadCommitted, config.scheduleTopics.map(_.value), idleTimeout, bufferSize, parallelism)
        TopicLoader(tlc, f andThen (_.fold(_ => Future.successful(None), loadSchedule(_).map(_.some))), new ByteArrayDeserializer)
      }

      ScheduleReader[KafkaMessage](
        reloadSchedules,
        Eval.later(KafkaStream.source(config.scheduleTopics)),
        actorRef,
        KafkaStream.commitOffset(config.offsetBatch),
        errorHandler[KafkaMessage, Ack.type],
        appRestartStrategy)
    }

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Running[KillSwitch, Future[Done]]] =
    Start { app =>
      val (srcMat, sinkMat) = app.reader.stream.toMat(Sink.ignore)(Keep.both).run()
      Running(srcMat, sinkMat)
    }
}
