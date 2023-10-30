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

  def produce[V](
      topic: String,
      keyValues: (String, Option[V])*
  )(using ValueSerializer[F, V]): F[Unit]

  def consume[V](
      topic: String,
      noMessages: Int,
      autoCommit: Boolean = true
  )(using ValueDeserializer[F, V]): F[List[ConsumerResult[V]]]

}

object KafkaUtil {

  final case class ConsumerResult[V](topic: String, key: String, value: V, producedAt: Instant, consumedAt: Instant) {
    val keyValue: (String, V) = key -> value
  }

  def apply[F[_] : Async](
      kafkaPort: Int,
      timeout: FiniteDuration
  ): Resource[F, KafkaUtil[F]] =
    Resource.pure {
      new KafkaUtil[F] {
        override def produce[V](
            topic: String,
            keyValues: (String, Option[V])*
        )(using ValueSerializer[F, V]): F[Unit] = {
          val producerSettings: ProducerSettings[F, String, Option[V]] =
            ProducerSettings[F, String, Option[V]]
              .withBootstrapServers(s"localhost:$kafkaPort")

          val records = Chunk.from(keyValues).map(ProducerRecord(topic, _, _))

          KafkaProducer
            .stream(producerSettings)
            .evalMap(_.produce(records))
            .parEvalMapUnbounded(identity)
            .compile
            .drain
        }

        override def consume[V](
            topic: String,
            noMessages: Int,
            autocommit: Boolean = true
        )(using ValueDeserializer[F, V]): F[List[ConsumerResult[V]]] = {
          val consumerSettings: ConsumerSettings[F, String, V] =
            ConsumerSettings[F, String, V]
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
