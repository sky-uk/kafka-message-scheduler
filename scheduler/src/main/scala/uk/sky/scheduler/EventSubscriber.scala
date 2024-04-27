package uk.sky.scheduler

import cats.effect.Resource.ExitCase
import cats.effect.{Async, Deferred, Ref}
import cats.syntax.all.*
import cats.{Monad, Parallel, Show}
import fs2.*
import fs2.kafka.*
import mouse.all.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.{Attribute, Attributes}
import uk.sky.fs2.kafka.topicloader.TopicLoader
import uk.sky.scheduler.circe.jsonScheduleDecoder
import uk.sky.scheduler.config.KafkaConfig
import uk.sky.scheduler.converters.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.avro.{avroBinaryDeserializer, avroScheduleCodec, AvroSchedule}
import uk.sky.scheduler.kafka.json.{jsonDeserializer, JsonSchedule}
import uk.sky.scheduler.message.Message

trait EventSubscriber[F[_]] {
  def messages: Stream[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]]
}

object EventSubscriber {
  private type Output = Either[ScheduleError, Option[ScheduleEvent]]

  def kafka[F[_] : Async : Parallel : LoggerFactory](
      config: KafkaConfig,
      loaded: Deferred[F, Unit]
  ): F[EventSubscriber[F]] = {

    val avroConsumerSettings: ConsumerSettings[F, String, Either[ScheduleError, Option[AvroSchedule]]] = {
      given Deserializer[F, Either[ScheduleError, Option[AvroSchedule]]] =
        avroBinaryDeserializer[F, AvroSchedule].option.map(_.sequence)

      config.consumerSettings[F, String, Either[ScheduleError, Option[AvroSchedule]]]
    }

    val jsonConsumerSettings: ConsumerSettings[F, String, Either[ScheduleError, Option[JsonSchedule]]] = {
      given Deserializer[F, Either[ScheduleError, Option[JsonSchedule]]] =
        jsonDeserializer[F, JsonSchedule].option.map(_.sequence)

      config.consumerSettings[F, String, Either[ScheduleError, Option[JsonSchedule]]]
    }

    for {
      avroLoadedRef <- Ref.of[F, Boolean](false)
      jsonLoadedRef <- Ref.of[F, Boolean](false)
    } yield new EventSubscriber[F] {

      /** If both topics have finished loading, complete the Deferred to allow Queueing schedules.
        */
      private def onLoadCompare(exitCase: ExitCase): F[Unit] =
        exitCase match {
          case ExitCase.Succeeded                      =>
            for {
              avroLoaded <- avroLoadedRef.get
              jsonLoaded <- jsonLoadedRef.get
              _          <- Async[F].whenA(avroLoaded && jsonLoaded)(loaded.complete(()))
            } yield ()
          case ExitCase.Errored(_) | ExitCase.Canceled => Async[F].unit
        }

      private val avroStream: Stream[F, ConsumerRecord[String, Either[ScheduleError, Option[AvroSchedule]]]] =
        config.topics.avro.toNel
          .fold(Stream.exec(avroLoadedRef.set(true) *> onLoadCompare(ExitCase.Succeeded)))(
            TopicLoader.loadAndRun(_, avroConsumerSettings) { exitCase =>
              avroLoadedRef.set(true) *> onLoadCompare(exitCase)
            }
          )

      private val jsonStream: Stream[F, ConsumerRecord[String, Either[ScheduleError, Option[JsonSchedule]]]] =
        config.topics.json.toNel
          .fold(Stream.exec(jsonLoadedRef.set(true) *> onLoadCompare(ExitCase.Succeeded)))(
            TopicLoader.loadAndRun(_, jsonConsumerSettings) { exitCase =>
              jsonLoadedRef.set(true) *> onLoadCompare(exitCase)
            }
          )

      override def messages: Stream[F, Message[Output]] =
        avroStream.merge(jsonStream).map(_.toMessage)
    }
  }

  def observed[F[_] : Monad : Parallel : LoggerFactory : Meter](delegate: EventSubscriber[F]): F[EventSubscriber[F]] = {
    given scheduleErrorType: Show[ScheduleError] = Show.show {
      case _: ScheduleError.InvalidAvroError => "invalid-avro"
      case _: ScheduleError.NotJsonError     => "not-json"
      case _: ScheduleError.InvalidJsonError => "invalid-json"
      case _: ScheduleError.DecodeError      => "decode"
    }

    def updateAttributes(source: String) = Attributes(
      Attribute("message.type", "update"),
      Attribute("message.source", source)
    )

    def deleteAttributes(source: String, deleteType: String) = Attributes(
      Attribute("message.type", "delete"),
      Attribute("message.source", source),
      Attribute("message.delete.type", deleteType)
    )

    def errorAttributes(source: String, error: ScheduleError) = Attributes(
      Attribute("message.type", "error"),
      Attribute("message.source", source),
      Attribute("message.error.type", error.show)
    )

    Meter[F].counter[Long]("event-subscriber").create.map { counter =>
      val logger = LoggerFactory[F].getLogger

      new EventSubscriber[F] {
        override def messages: Stream[F, Message[Output]] = delegate.messages.evalTapChunk { message =>
          import message.*

          val logCtx = Map("key" -> key, "source" -> source)

          message.value match {
            case Right(Some(_)) =>
              logger.info(logCtx)(s"Decoded UPDATE for [$key] from $source") &>
                counter.inc(updateAttributes(source))

            case Right(None) =>
              val deleteType = message.isExpired.fold("expired", "canceled")
              logger.info(logCtx)(s"Decoded DELETE type=[$deleteType] for [$key] from $source") &>
                counter.inc(deleteAttributes(source, deleteType))

            case Left(error) =>
              logger.error(logCtx, error)(s"Error decoding [$key] from $source - ${error.getMessage}") &>
                counter.inc(errorAttributes(source, error))
          }
        }
      }
    }
  }

  def live[F[_] : Async : Parallel : LoggerFactory : Meter](
      config: KafkaConfig,
      loaded: Deferred[F, Unit]
  ): F[EventSubscriber[F]] =
    kafka[F](config, loaded).flatMap(observed)
}
