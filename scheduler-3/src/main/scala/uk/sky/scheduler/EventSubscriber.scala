package uk.sky.scheduler

import cats.Parallel
import cats.effect.{Async, Ref, Resource}
import cats.effect.kernel.Deferred
import cats.syntax.all.*
import fs2.Stream
import fs2.kafka.*
import org.typelevel.log4cats.LoggerFactory
import uk.sky.fs2.kafka.topicloader.TopicLoader
import uk.sky.scheduler.config.{Config, KafkaConfig}
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.avro.{AvroSchedule, avroBinaryDeserializer, avroScheduleCodec}
import uk.sky.scheduler.message.Message

trait EventSubscriber[F[_]] {
  def messages: Stream[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]]
}

object EventSubscriber {
  private type Output = Either[ScheduleError, Option[ScheduleEvent]]

  def kafka[F[_] : Async : Parallel : LoggerFactory](
    config: Config,
    loaded: Deferred[F, Unit]
  ): F[EventSubscriber[F]] = {

    val avroConsumerSettings: ConsumerSettings[F, String, Either[ScheduleError, Option[AvroSchedule]]] = {
      given Resource[F, Deserializer[F, Either[ScheduleError, Option[AvroSchedule]]]] =
        avroBinaryDeserializer[F, AvroSchedule].map(_.option.map(_.sequence))

      config.kafka.consumerSettings[F, String, Either[ScheduleError, Option[AvroSchedule]]]
    }

    new EventSubscriber[F] {
      private val avroStream: Stream[F, ConsumerRecord[String, Either[ScheduleError, Option[AvroSchedule]]]] =
        ???
    }

  }
}
