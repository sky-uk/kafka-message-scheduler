package uk.sky.scheduler

import cats.Monad
import cats.effect.Resource.ExitCase
import cats.effect.{Async, Deferred, Ref}
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import org.typelevel.log4cats.LoggerFactory
import uk.sky.fs2.kafka.topicloader.TopicLoader
import uk.sky.scheduler.circe.jsonScheduleDecoder
import uk.sky.scheduler.config.ScheduleConfig
import uk.sky.scheduler.converters.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.avro.{avroBinaryDeserializer, avroScheduleCodec, AvroSchedule}
import uk.sky.scheduler.kafka.json.{jsonDeserializer, JsonSchedule}

trait EventSubscriber[F[_]] {
  def messages: Stream[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]]
}

object EventSubscriber {
  private type Output = Either[ScheduleError, Option[ScheduleEvent]]

  def kafka[F[_] : Async : LoggerFactory](config: ScheduleConfig, loaded: Deferred[F, Unit]): F[EventSubscriber[F]] = {
    type Input = JsonSchedule | AvroSchedule

    def toEvent(cr: ConsumerRecord[String, Either[Throwable, Option[Input]]]): Message[Output] = {
      val key = cr.key

      val payload: Either[ScheduleError, Option[ScheduleEvent]] = cr.value match {
        case Left(error)        => ScheduleError.DecodeError(key, error.getMessage).asLeft
        case Right(None)        => none[ScheduleEvent].asRight[ScheduleError]
        case Right(Some(input)) =>
          val scheduleEvent = input match {
            case avroSchedule: AvroSchedule => avroSchedule.scheduleEvent(cr.key, cr.topic)
            case jsonSchedule: JsonSchedule => jsonSchedule.scheduleEvent(cr.key, cr.topic)
          }
          scheduleEvent.some.asRight[ScheduleError]
      }

      Message[Output](
        key = cr.key,
        source = cr.topic,
        value = payload,
        headers = cr.headers.toChain.toList.map(header => header.key -> header.as[String]).toMap
      )
    }

    /** If both topics have finished loading, complete the Deferred to allow Queueing schedules.
      */
    def onLoadCompare(ref: Ref[F, Boolean], other: Ref[F, Boolean])(exitCase: ExitCase): F[Unit] =
      exitCase match {
        case ExitCase.Succeeded                      =>
          for {
            _         <- ref.set(true)
            otherDone <- other.get
            _         <- if (otherDone) loaded.complete(()).void else Async[F].unit
          } yield ()
        case ExitCase.Errored(_) | ExitCase.Canceled => Async[F].unit
      }

    for {
      avroRef <- Ref.of[F, Boolean](false)
      jsonRef <- Ref.of[F, Boolean](false)
    } yield new EventSubscriber[F] {

      given avroDeser: Deserializer[F, Either[Throwable, Option[AvroSchedule]]] =
        avroBinaryDeserializer[F, AvroSchedule].option.attempt

      given jsonDeser: Deserializer[F, Either[Throwable, Option[JsonSchedule]]] =
        jsonDeserializer[F, JsonSchedule].option.attempt

      val avroConsumerSettings =
        ConsumerSettings[F, String, Either[Throwable, Option[AvroSchedule]]]
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(config.kafka.bootstrapServers)
          .withProperties(config.kafka.properties)

      val jsonConsumerSettings =
        ConsumerSettings[F, String, Either[Throwable, Option[JsonSchedule]]]
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(config.kafka.bootstrapServers)
          .withProperties(config.kafka.properties)

      val avroStream =
        config.kafka.topics.avro.toNel.fold(Stream.eval(avroRef.set(true)).drain)(
          TopicLoader.loadAndRun(_, avroConsumerSettings)(onLoadCompare(avroRef, jsonRef))
        )

      val jsonStream =
        config.kafka.topics.json.toNel.fold(Stream.eval(jsonRef.set(true)).drain)(
          TopicLoader.loadAndRun(_, jsonConsumerSettings)(onLoadCompare(jsonRef, avroRef))
        )

      override def messages: Stream[F, Message[Output]] =
        avroStream.merge(jsonStream).map(toEvent)
    }
  }

  def observed[F[_] : Monad : LoggerFactory](delegate: EventSubscriber[F]): EventSubscriber[F] = {
    val logger = LoggerFactory[F].getLogger

    new EventSubscriber[F] {
      override def messages: Stream[F, Message[Output]] = delegate.messages.evalTap { message =>
        val key   = message.key
        val topic = message.source
        message.value match {
          case Left(error)    => logger.error(error)(s"Error decoding [$key] from topic $topic - ${error.getMessage}")
          case Right(None)    => logger.info(s"Decoded DELETE for [$key] from topic $topic")
          case Right(Some(_)) => logger.info(s"Decoded UPDATE for [$key] from topic $topic")
        }
      }.onFinalizeCase {
        case ExitCase.Succeeded  => logger.info("Stream Succeeded")
        case ExitCase.Errored(e) => logger.error(e)(s"Stream error - ${e.getMessage}")
        case ExitCase.Canceled   => logger.info("Stream canceled")
      }
    }
  }

  def live[F[_] : Async : LoggerFactory](config: ScheduleConfig, loaded: Deferred[F, Unit]): F[EventSubscriber[F]] =
    kafka[F](config, loaded).map(observed)
}
