package uk.sky.scheduler.util

import java.time.Instant

import cats.effect.syntax.all.*
import cats.effect.{Async, Resource}
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import uk.sky.scheduler.util.KafkaUtil.ConsumerResult

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
  ): F[List[ConsumerResult]]

}

object KafkaUtil {

  final case class ConsumerResult(topic: String, key: String, value: String, producedAt: Instant, consumedAt: Instant) {
    val keyValue: (String, String) = key -> value
  }

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
        ): F[List[ConsumerResult]] = {
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
            .evalMap(cr =>
              for {
                consumedAt <- Async[F].realTimeInstant
                producedAt <- {
                  val ts = cr.record.timestamp
                  (ts.createTime orElse ts.unknownTime orElse ts.logAppendTime)
                    .map(Instant.ofEpochMilli.apply)
                    .liftTo(IllegalStateException("Record timestamp was empty"))
                }
              } yield ConsumerResult(
                topic = topic,
                key = cr.record.key,
                value = cr.record.value,
                producedAt = producedAt,
                consumedAt = consumedAt
              )
            )
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
