package uk.sky.scheduler

import cats.Monad
import cats.effect.Async
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import org.typelevel.log4cats.LoggerFactory
import uk.sky.scheduler.circe.jsonScheduleDecoder
import uk.sky.scheduler.config.ScheduleConfig
import uk.sky.scheduler.converters.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.Message
import uk.sky.scheduler.kafka.avro.{avroBinaryDeserializer, avroScheduleCodec, AvroSchedule}
import uk.sky.scheduler.kafka.json.{jsonDeserializer, JsonSchedule}

trait EventSubscriber[F[_]] {
  def messages: Stream[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]]
}

object EventSubscriber {
  private type Output = Either[ScheduleError, Option[ScheduleEvent]]

  def kafka[F[_] : Async](config: ScheduleConfig): EventSubscriber[F] = {
    type Input = JsonSchedule | AvroSchedule

    def toEvent(cr: ConsumerRecord[String, Either[Throwable, Option[Input]]]): Message[Output] = {
      val key = cr.key

      val payload = cr.value match {
        case Left(error)        => ScheduleError.DecodeError(key, error.getMessage).asLeft
        case Right(None)        => none[ScheduleEvent].asRight[ScheduleError]
        case Right(Some(input)) =>
          val scheduleEvent = input match {
            case avroSchedule: AvroSchedule => avroSchedule.scheduleEvent
            case jsonSchedule: JsonSchedule => jsonSchedule.scheduleEvent
          }
          scheduleEvent.some.asRight[ScheduleError]
      }

      Message[Output](cr.key, cr.topic, payload)
    }

    new EventSubscriber[F] {

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

      val avroStream: Stream[F, ConsumerRecord[String, Either[Throwable, Option[Input]]]] =
        config.kafka.topics.avro.toNel.fold(Stream.empty)(
          KafkaConsumer.stream(avroConsumerSettings).subscribe(_).records.map(_.record)
        )

      val jsonStream: Stream[F, ConsumerRecord[String, Either[Throwable, Option[Input]]]] =
        config.kafka.topics.json.toNel.fold(Stream.empty)(
          KafkaConsumer.stream(jsonConsumerSettings).subscribe(_).records.map(_.record)
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
        val topic = message.topic
        message.value match {
          case Left(error)    => logger.error(error)(s"Error decoding [$key] from topic $topic - ${error.getMessage}")
          case Right(None)    => logger.info(s"Decoded DELETE for [$key] from topic $topic")
          case Right(Some(_)) => logger.info(s"Decoded UPDATE for [$key] from topic $topic")
        }
      }
    }
  }
}
