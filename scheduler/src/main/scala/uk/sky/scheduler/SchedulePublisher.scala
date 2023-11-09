package uk.sky.scheduler

import cats.effect.Async
import cats.effect.std.Queue
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import uk.sky.scheduler.config.ScheduleConfig
import uk.sky.scheduler.converters.*
import uk.sky.scheduler.domain.ScheduleEvent

trait SchedulePublisher[F[_], O] {
  def publish: Stream[F, O]
}

object SchedulePublisher {
  def kafka[F[_] : Async](config: ScheduleConfig, eventQueue: Queue[F, ScheduleEvent]): SchedulePublisher[F, Unit] = {

    given outputSerializer: ValueSerializer[F, Option[Array[Byte]]] =
      Serializer.identity.option

    val producerSettings: ProducerSettings[F, Array[Byte], Option[Array[Byte]]] =
      ProducerSettings[F, Array[Byte], Option[Array[Byte]]]
        .withBootstrapServers(config.kafka.bootstrapServers)
        .withProperties(config.kafka.properties)

    new SchedulePublisher[F, Unit] {
      override def publish: Stream[F, Unit] =
        KafkaProducer
          .stream(producerSettings)
          .flatMap { producer =>
            Stream
              .fromQueueUnterminated(eventQueue)
              .evalMap { scheduleEvent =>
                val scheduleProducerRecord = scheduleEvent.toProducerRecord
                val tombstone              = scheduleEvent.toTombstone
                producer.produce(ProducerRecords(List(scheduleProducerRecord, tombstone)))
              }
              .parEvalMapUnbounded(identity)
          }
          .void
    }
  }

}
