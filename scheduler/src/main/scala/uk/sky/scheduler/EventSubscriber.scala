package uk.sky.scheduler

import cats.effect.Resource.ExitCase
import cats.effect.{Async, Deferred, Ref}
import cats.syntax.all.*
import cats.{Monad, Parallel}
import fs2.*
import fs2.kafka.*
import org.typelevel.log4cats.LoggerFactory
import uk.sky.fs2.kafka.topicloader.TopicLoader
import uk.sky.scheduler.circe.jsonScheduleDecoder
import uk.sky.scheduler.config.KafkaConfig
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

  def kafka[F[_] : Async : Parallel : LoggerFactory](
      config: KafkaConfig,
      loaded: Deferred[F, Unit]
  ): F[EventSubscriber[F]] = {
    type Input = JsonSchedule | AvroSchedule

    def toEvent(cr: ConsumerRecord[String, Either[ScheduleError, Option[Input]]]): Message[Output] = {
      val key   = cr.key
      val topic = cr.topic

      val payload: Either[ScheduleError, Option[ScheduleEvent]] = cr.value match {
        case Left(error)        => ScheduleError.DecodeError(key, error.getMessage).asLeft
        case Right(None)        => none[ScheduleEvent].asRight[ScheduleError]
        case Right(Some(input)) =>
          val scheduleEvent = input match {
            case avroSchedule: AvroSchedule => avroSchedule.scheduleEvent(key, topic)
            case jsonSchedule: JsonSchedule => jsonSchedule.scheduleEvent(key, topic)
          }
          scheduleEvent.some.asRight[ScheduleError]
      }

      Message[Output](
        key = key,
        source = topic,
        value = payload,
        headers = Message.Headers(cr.headers.toChain.map(header => header.key -> header.as[String]).toList.toMap)
      )
    }

    for {
      avroLoadedRef <- Ref.of[F, Boolean](false)
      jsonLoadedRef <- Ref.of[F, Boolean](false)
    } yield new EventSubscriber[F] {

      /** If both topics have finished loading, complete the Deferred to allow Queueing schedules.
        */
      def onLoadCompare(exitCase: ExitCase): F[Unit] =
        exitCase match {
          case ExitCase.Succeeded                      =>
            for {
              results <- (avroLoadedRef.get, jsonLoadedRef.get).parTupled
              _       <- if (results.forall(_ == true)) loaded.complete(()).void else Async[F].unit
            } yield ()
          case ExitCase.Errored(_) | ExitCase.Canceled => Async[F].unit
        }

      given avroDeser: Deserializer[F, Either[ScheduleError, Option[AvroSchedule]]] =
        avroBinaryDeserializer[F, AvroSchedule].option.map(_.sequence)

      given jsonDeser: Deserializer[F, Either[ScheduleError, Option[JsonSchedule]]] =
        jsonDeserializer[F, JsonSchedule].option.map(_.sequence)

      val avroConsumerSettings =
        ConsumerSettings[F, String, Either[ScheduleError, Option[AvroSchedule]]]
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(config.bootstrapServers)
          .withProperties(config.properties)

      val jsonConsumerSettings =
        ConsumerSettings[F, String, Either[ScheduleError, Option[JsonSchedule]]]
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(config.bootstrapServers)
          .withProperties(config.properties)

      val avroStream =
        config.topics.avro.toNel
          .fold(Stream.exec(avroLoadedRef.set(true) *> onLoadCompare(ExitCase.Succeeded)))(
            TopicLoader.loadAndRun(_, avroConsumerSettings)(exitCase =>
              avroLoadedRef.set(true) *> onLoadCompare(exitCase)
            )
          )

      val jsonStream =
        config.topics.json.toNel
          .fold(Stream.exec(jsonLoadedRef.set(true) *> onLoadCompare(ExitCase.Succeeded)))(
            TopicLoader.loadAndRun(_, jsonConsumerSettings)(exitCase =>
              jsonLoadedRef.set(true) *> onLoadCompare(exitCase)
            )
          )

      override def messages: Stream[F, Message[Output]] =
        avroStream.merge(jsonStream).map(toEvent)
    }
  }

  def observed[F[_] : Monad : LoggerFactory](delegate: EventSubscriber[F]): EventSubscriber[F] = {
    val logger = LoggerFactory[F].getLogger

    new EventSubscriber[F] {
      override def messages: Stream[F, Message[Output]] = delegate.messages.evalTapChunk { message =>
        val key    = message.key
        val source = message.source

        message.value match {
          case Left(error) =>
            logger.error(error)(s"Error decoding [$key] from $source - ${error.getMessage}")

          case Right(None) =>
            val deleteType = if (message.expired) "expired" else "canceled"
            logger.info(s"Decoded DELETE type=[$deleteType] for [$key] from $source")

          case Right(Some(_)) =>
            logger.info(s"Decoded UPDATE for [$key] from $source")
        }
      }
    }
  }

  def live[F[_] : Async : Parallel : LoggerFactory](
      config: KafkaConfig,
      loaded: Deferred[F, Unit]
  ): F[EventSubscriber[F]] =
    kafka[F](config, loaded).map(observed)
}
