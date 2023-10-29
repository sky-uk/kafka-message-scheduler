package uk.sky.scheduler.util

import cats.effect.syntax.all.*
import cats.effect.{Async, Resource}
import fs2.*
import fs2.kafka.*

import scala.concurrent.duration.FiniteDuration

trait KafkaUtil[F[_]] {

  def produce(
      topic: String,
      key: String,
      value: String
  ): F[Unit]

  def consume(
      topic: String,
      noMessages: Int,
      autoCommit: Boolean = true
  ): F[List[(String, String)]]

}

object KafkaUtil {

  def apply[F[_] : Async](
      kafkaPort: Int,
      timeout: FiniteDuration
  ): Resource[F, KafkaUtil[F]] =
    Resource.pure {
      new KafkaUtil[F] {
        override def produce(
            topic: String,
            key: String,
            value: String
        ): F[Unit] = {
          val producerSettings: ProducerSettings[F, String, String] =
            ProducerSettings[F, String, String]
              .withBootstrapServers(s"localhost:$kafkaPort")

          val record = ProducerRecord(topic, key, value)

          KafkaProducer
            .stream(producerSettings)
            .evalMap { producer =>
              producer.produce(ProducerRecords.one(record))
            }
            .parEvalMapUnbounded(identity)
            .compile
            .drain
        }

        override def consume(
            topic: String,
            noMessages: Int,
            autocommit: Boolean = true
        ): F[List[(String, String)]] = {
          val consumerSettings: ConsumerSettings[F, String, String] =
            ConsumerSettings[F, String, String]
              .withAutoOffsetReset(AutoOffsetReset.Earliest)
              .withBootstrapServers(s"localhost:$kafkaPort")
              .withGroupId("integration-test-consumer-group")
              .withEnableAutoCommit(autocommit)

          KafkaConsumer
            .stream(consumerSettings)
            .subscribeTo(topic)
            .records
            .map(cr => cr.record.key -> cr.record.value)
            .take(noMessages)
            .compile
            .toList
            .timeoutTo(
              timeout,
              Async[F].raiseError(IllegalStateException(s"Could not consume $noMessages messages within $timeout"))
            )
        }
      }
    }

}
