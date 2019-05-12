package com.sky.kms.streams

import akka.Done
import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl._
import cats.Eval
import cats.instances.either._
import cats.instances.future._
import cats.syntax.option._
import cats.syntax.traverse._
import com.sky.kafka.topicloader._
import com.sky.kms._
import com.sky.kms.actors.SchedulingActor
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.config._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka._
import com.sky.kms.streams.ScheduleReader.{In, LoadSchedule}
import com.sky.map.commons.akka.streams.BackoffRestartStrategy
import com.sky.map.commons.akka.streams.utils.Restartable._
import com.sky.map.commons.akka.streams.utils.SourceOps._
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer

import scala.concurrent.Future

/**
  * Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader(loadProcessedSchedules: LoadSchedule => Source[_, _],
                                                     scheduleSource: Eval[Source[In, _]],
                                                     schedulingActor: ActorRef,
                                                     errorHandler: Sink[ApplicationError, Future[Done]],
                                                     restartStrategy: BackoffRestartStrategy,
                                                     timeouts: ReaderConfig.TimeoutConfig)(
                                                      implicit system: ActorSystem) {

  import system.dispatcher

  def stream: Source[Either[ApplicationError, SchedulingActor.Ack.type], KillSwitch] =
    scheduleSource.value
      .map(ScheduleReader.toSchedulingMessage)
      .mapAsync(Parallelism)(_.traverse(processSchedulingMessage))
      .alsoTo(extractError.to(errorHandler))
      .restartUsing(restartStrategy)
      .watchTermination() { case (mat, fu) => fu.failed.foreach(schedulingActor ! UpstreamFailure(_)); mat }
      .runAfter(loadProcessedSchedules(processSchedulingMessage).watchTermination() { case (_, fu) =>
        fu
          .flatMap(_ => (schedulingActor ? Initialised)(timeouts.initialisation))
          .recover { case t => schedulingActor ! UpstreamFailure(t) }
      })

  private def processSchedulingMessage(msg: SchedulingMessage): Future[SchedulingActor.Ack.type] =
    (schedulingActor ? msg)(timeouts.scheduling).mapTo[Ack.type]

}

object ScheduleReader extends LazyLogging {

  case class Running[SrcMat, SinkMat](materializedSource: SrcMat, materializedSink: SinkMat)

  type In = Either[ApplicationError, (ScheduleId, Option[ScheduleEvent])]
  type LoadSchedule = SchedulingMessage => Future[Ack.type]

  def toSchedulingMessage(readResult: In): Either[ApplicationError, SchedulingMessage] =
    readResult.map { case (scheduleId, scheduleOpt) =>
      scheduleOpt.fold[SchedulingMessage](Cancel(scheduleId))(CreateOrUpdate(scheduleId, _))
    }

  def configure(actorRef: ActorRef)(implicit system: ActorSystem): Configured[ScheduleReader] =
    ReaderConfig.configure.map { config =>
      def reloadSchedules(loadSchedule: LoadSchedule) = {
        import system.dispatcher
        val f = (cr: ConsumerRecord[String, Array[Byte]]) => toSchedulingMessage(scheduleConsumerRecordDecoder(cr))
        TopicLoader(LoadAll, config.scheduleTopics.map(_.value), f andThen (
          _.fold(_ => Future.successful(None), loadSchedule(_).map(_.some))), new ByteArrayDeserializer)
      }

      ScheduleReader(
        reloadSchedules,
        Eval.later(KafkaStream.source(config.scheduleTopics)),
        actorRef,
        logErrors,
        config.restartStrategy,
        config.timeouts)
    }

  def run(implicit mat: ActorMaterializer): Start[Running[KillSwitch, Future[Done]]] =
    Start { app =>
      val (srcMat, sinkMat) = app.reader.stream.toMat(Sink.ignore)(Keep.both).run()
      Running(srcMat, sinkMat)
    }
}
