package com.sky.kms.streams

import akka.Done
import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.kafka.scaladsl.Consumer.Control
import akka.pattern.ask
import akka.stream.scaladsl._
import cats.Eval
import com.sky.kafka.topicloader._
import com.sky.kms._
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.config._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.streams.ScheduleReader.In
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, Deserializer, StringDeserializer}

import scala.concurrent.Future

/** Provides stream from the schedule source to the scheduling actor.
  */
case class ScheduleReader[Mat](
    scheduleSource: Eval[Source[In, (Future[Done], Mat)]],
    schedulingActor: ActorRef,
    errorHandler: Sink[ApplicationError, Future[Done]],
    timeouts: ReaderConfig.TimeoutConfig
)(implicit system: ActorSystem) {

  import system.dispatcher

  private def initSchedulingActorWhenReady(f: Future[Done]): Future[Any] =
    f.flatMap(_ => (schedulingActor ? Initialised)(timeouts.initialisation)).recover { case t =>
      schedulingActor ! UpstreamFailure(t)
    }

  def stream: RunnableGraph[Mat] =
    scheduleSource.value.mapMaterializedValue { case (initF, mat) => initSchedulingActorWhenReady(initF); mat }
      .map(ScheduleReader.toSchedulingMessage)
      .alsoTo(extractError.to(errorHandler))
      .collect { case Right(msg) => msg }
      .to(Sink.actorRefWithBackpressure(schedulingActor, StreamStarted, Ack, PoisonPill, UpstreamFailure))
}

object ScheduleReader extends LazyLogging {

  case class Running[Mat](mat: Mat)

  type In           = Either[ApplicationError, (ScheduleId, Option[ScheduleEvent])]
  type LoadSchedule = SchedulingMessage => Future[Ack.type]

  def toSchedulingMessage(readResult: In): Either[ApplicationError, SchedulingMessage] =
    readResult.map { case (scheduleId, scheduleOpt) =>
      scheduleOpt.fold[SchedulingMessage](Cancel(scheduleId))(CreateOrUpdate(scheduleId, _))
    }

  def configure(actorRef: ActorRef)(implicit system: ActorSystem): Configured[ScheduleReader[Future[Control]]] =
    ReaderConfig.configure.map { config =>
      implicit val keyDeserializer: Deserializer[String]        = new StringDeserializer()
      implicit val valueDeserializer: Deserializer[Array[Byte]] = new ByteArrayDeserializer()

      ScheduleReader(
        Eval.always(
          TopicLoader
            .loadAndRun[String, Array[Byte]](config.scheduleTopics.map(_.value))
            .map(scheduleConsumerRecordDecoder(_))
        ),
        actorRef,
        logErrors,
        config.timeouts
      )
    }

  def run(implicit system: ActorSystem): Start[Running[Future[Control]]] =
    Start { app =>
      Running(app.reader.stream.run())
    }
}
