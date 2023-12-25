package uk.sky.scheduler

import cats.effect.Async
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import uk.sky.scheduler.config.KafkaConfig
import uk.sky.scheduler.converters.*
import uk.sky.scheduler.domain.ScheduleEvent

trait SchedulePublisher[F[_], O] {
  def publish: Pipe[F, ScheduleEvent, O]
}

object SchedulePublisher {
  def kafka[F[_] : Async](config: KafkaConfig): SchedulePublisher[F, Unit] = {

    given outputSerializer: ValueSerializer[F, Option[Array[Byte]]] =
      Serializer.identity.option

    val producerSettings: ProducerSettings[F, Array[Byte], Option[Array[Byte]]] =
      ProducerSettings[F, Array[Byte], Option[Array[Byte]]]
        .withBootstrapServers(config.bootstrapServers)
        .withProperties(KafkaConfig.atLeastOnce)
        .withProperties(KafkaConfig.performant)
        .withProperties(config.properties)

    new SchedulePublisher[F, Unit] {
      override def publish: Pipe[F, ScheduleEvent, Unit] = scheduleEventStream =>
        KafkaProducer
          .stream(producerSettings)
          .flatMap { producer =>
            scheduleEventStream.evalMapChunk { scheduleEvent =>
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
