package uk.sky.scheduler

import cats.effect.Async
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import uk.sky.scheduler.config.KafkaConfig
import uk.sky.scheduler.converters.*
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.kafka.*

trait SchedulePublisher[F[_], O] {
  def publish: Pipe[F, ScheduleEvent, O]
}

object SchedulePublisher {
  def kafka[F[_] : Async](config: KafkaConfig): SchedulePublisher[F, Unit] = {

    val producerSettings: ProducerSettings[F, Array[Byte], Option[Array[Byte]]] =
      config
        .producerSettings[F, Array[Byte], Option[Array[Byte]]]
        .atLeastOnce
        .performant

    new SchedulePublisher[F, Unit] {
      override def publish: Pipe[F, ScheduleEvent, Unit] = scheduleEventStream =>
        KafkaProducer
          .stream(producerSettings)
          .flatMap { producer =>
            scheduleEventStream.evalMapChunk { scheduleEvent =>
              producer.produce {
                ProducerRecords(List(scheduleEvent.toProducerRecord, scheduleEvent.toTombstone))
              }
            }
              .groupWithin(config.producer.batchSize, config.producer.timeout)
              .evalMapChunk(_.sequence)
          }
          .void
    }
  }

}
