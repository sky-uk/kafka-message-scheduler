package uk.sky.scheduler

import cats.effect.Resource.ExitCase
import cats.effect.{Async, Deferred, Ref, Resource}
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
import uk.sky.scheduler.config.Config
import uk.sky.scheduler.converters.all.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.avro.{*, given}
import uk.sky.scheduler.kafka.json.{jsonDeserializer, JsonSchedule}
import uk.sky.scheduler.message.Message

trait EventSubscriber[F[_]] {
  def messages: Stream[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]]
}

object EventSubscriber {
  private type Output = Either[ScheduleError, Option[ScheduleEvent]]

  def kafka[F[_] : Async : LoggerFactory](
      config: Config,
      loaded: Deferred[F, Unit]
  ): F[EventSubscriber[F]] = {

    val avroConsumerSettings: ConsumerSettings[F, String, Either[ScheduleError, Option[AvroSchedule]]] = {
      given Resource[F, ValueDeserializer[F, Either[ScheduleError, Option[AvroSchedule]]]] = {
        val scheduleDeserializer: Resource[F, ValueDeserializer[F, Either[ScheduleError, Option[AvroSchedule]]]] =
          avroBinaryDeserializer[F, AvroSchedule].map(_.option.map(_.sequence))
        val scheduleWithoutHeadersDeserializer: Resource[
          F,
          ValueDeserializer[F, Either[ScheduleError, Option[AvroSchedule]]]
        ] =
          avroBinaryDeserializer[F, AvroScheduleWithoutHeaders].map(_.option.map(_.sequence.map(_.map(_.avroSchedule))))

        for {
          sDeserializer   <- scheduleDeserializer
          swhDeserializer <- scheduleWithoutHeadersDeserializer
        } yield sDeserializer.flatMap {
          case Right(maybeSchedule) =>
            GenericDeserializer
              .const[F, Either[ScheduleError, Option[AvroSchedule]]](Right(maybeSchedule))

          case Left(_) => swhDeserializer
        }
      }

      config.kafka.consumerSettings[F, String, Either[ScheduleError, Option[AvroSchedule]]]
    }

    val jsonConsumerSettings: ConsumerSettings[F, String, Either[ScheduleError, Option[JsonSchedule]]] = {
      given Deserializer[F, Either[ScheduleError, Option[JsonSchedule]]] =
        jsonDeserializer[F, JsonSchedule].option.map(_.sequence)

      config.kafka.consumerSettings[F, String, Either[ScheduleError, Option[JsonSchedule]]]
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
            TopicLoader.loadAndRun(_, avroConsumerSettings)(avroLoadedRef.set(true) >> onLoadCompare(_))
          )

      private val jsonStream: Stream[F, ConsumerRecord[String, Either[ScheduleError, Option[JsonSchedule]]]] =
        config.topics.json.toNel
          .fold(Stream.exec(jsonLoadedRef.set(true) *> onLoadCompare(ExitCase.Succeeded)))(
            TopicLoader.loadAndRun(_, jsonConsumerSettings)(jsonLoadedRef.set(true) >> onLoadCompare(_))
          )

      override def messages: Stream[F, Message[Output]] =
        avroStream.merge(jsonStream).map(_.toMessage)
    }
  }

  def observed[F[_] : Monad : Parallel : LoggerFactory : Meter](delegate: EventSubscriber[F]): F[EventSubscriber[F]] = {
    given Show[ScheduleError] = {
      case _: ScheduleError.InvalidAvroError    => "invalid-avro"
      case _: ScheduleError.NotJsonError        => "not-json"
      case _: ScheduleError.InvalidJsonError    => "invalid-json"
      case _: ScheduleError.DecodeError         => "decode"
      case _: ScheduleError.TransformationError => "transformation"
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

    for {
      counter <- Meter[F].counter[Long]("event-subscriber").create
      logger  <- LoggerFactory[F].create
    } yield new EventSubscriber[F] {
      override def messages: Stream[F, Message[Output]] =
        delegate.messages.evalTapChunk { case Message(key, source, value, metadata) =>
          val logCtx = Map("key" -> key, "source" -> source)

          value match {
            case Right(Some(_)) =>
              logger.info(logCtx)(show"Decoded UPDATE for [$key] from $source") &>
                counter.inc(updateAttributes(source))

            case Right(None) =>
              lazy val deleteType = metadata.isExpired.fold("expired", "canceled")
              logger.info(logCtx)(show"Decoded DELETE type=[$deleteType] for [$key] from $source") &>
                counter.inc(deleteAttributes(source, deleteType))

            case Left(error) =>
              logger.error(logCtx, error)(show"Error decoding [$key] from $source") &>
                counter.inc(errorAttributes(source, error))
          }
        }
    }
  }

  def live[F[_] : Async : Parallel : LoggerFactory : Meter](
      config: Config,
      loaded: Deferred[F, Unit]
  ): F[EventSubscriber[F]] =
    kafka[F](config, loaded).flatMap(observed)

}
