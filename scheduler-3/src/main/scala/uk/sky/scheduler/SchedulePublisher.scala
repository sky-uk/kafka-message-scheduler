package uk.sky.scheduler

import cats.Parallel
import cats.data.Reader
import cats.effect.Async
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.config.KafkaConfig
import uk.sky.scheduler.converters.scheduleEvent.*
import uk.sky.scheduler.domain.ScheduleEvent

trait SchedulePublisher[F[_], O] {
  def publish: Pipe[F, ScheduleEvent, O]
}

object SchedulePublisher {
  def kafka[F[_] : Async](config: KafkaConfig): SchedulePublisher[F, Unit] =
    new SchedulePublisher[F, Unit] {
      private val producerSettings = config.producerSettings[F, Array[Byte], Option[Array[Byte]]]

      override def publish: Pipe[F, ScheduleEvent, Unit] = scheduleEventStream =>
        for {
          producer <- KafkaProducer.stream(producerSettings)
          _        <- scheduleEventStream.chunks.evalMapChunk { scheduleEventChunk =>
                        val producerRecordChunk = scheduleEventChunk.flatMap(scheduleEvent =>
                          Chunk(scheduleEvent.toProducerRecord, scheduleEvent.toTombstone)
                        )
                        producer.produce(producerRecordChunk).flatten
                      }
        } yield ()
    }
  
  def live[F[_] : Async : Parallel : LoggerFactory : Meter]: Reader[KafkaConfig, SchedulePublisher[F, Unit]] = Reader {
    config =>
      kafka[F](config)
  }
}
