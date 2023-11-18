package uk.sky.scheduler.util

import java.time.Instant

import cats.effect.Async
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import org.apache.kafka.common.TopicPartition
import org.scalatest.exceptions.TestFailedException
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

  def consumeLast[V](
      topic: String,
      noMessages: Int,
      autoCommit: Boolean = true
  )(using ValueDeserializer[F, V]): F[List[ConsumerResult[V]]]
}

object KafkaUtil {

  final case class ConsumerResult[V](
      topic: String,
      key: String,
      value: V,
      producedAt: Instant,
      consumedAt: Instant,
      headers: Map[String, String]
  ) {
    val keyValue: (String, V) = key -> value
  }

  def apply[F[_] : Async](
      kafkaPort: Int,
      timeout: FiniteDuration
  ): KafkaUtil[F] = new KafkaUtil[F] {
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
        autoCommit: Boolean = true
    )(using ValueDeserializer[F, V]): F[List[ConsumerResult[V]]] = {
      val consumerSettings: ConsumerSettings[F, String, V] =
        ConsumerSettings[F, String, V]
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(s"localhost:$kafkaPort")
          .withGroupId("integration-test-consumer-group")
          .withEnableAutoCommit(autoCommit)

      KafkaConsumer
        .stream(consumerSettings)
        .subscribeTo(topic)
        .records
        .evalMap(toResult)
        .take(noMessages)
        .compile
        .toList
        .timeoutTo(
          timeout,
          Async[F].raiseError(TestFailedException(s"Could not consume $noMessages messages within $timeout", 0))
        )
    }

    override def consumeLast[V](
        topic: String,
        noMessages: Int,
        autoCommit: Boolean = true
    )(using ValueDeserializer[F, V]): F[List[ConsumerResult[V]]] = {
      val consumerSettings: ConsumerSettings[F, String, V] =
        ConsumerSettings[F, String, V]
          .withAutoOffsetReset(AutoOffsetReset.Earliest)
          .withBootstrapServers(s"localhost:$kafkaPort")
          .withGroupId("integration-test-consumer-group")
          .withEnableAutoCommit(autoCommit)

      KafkaConsumer
        .stream(consumerSettings)
        .evalTap { consumer =>
          // Assign to offsets n - 1
          for {
            tps        <- consumer.partitionsFor(topic).map(_.map(pi => TopicPartition(pi.topic, pi.partition)).toSet)
            endOffsets <- consumer.endOffsets(tps)
            newOffsets  = endOffsets.view.mapValues(o => Math.max(0, o - noMessages)).toList
            _          <- consumer.assign(topic)
            _          <- newOffsets.traverse(consumer.seek)
          } yield ()
        }
        .records
        .evalMap(toResult)
        .take(noMessages)
        .compile
        .toList
        .timeoutTo(
          timeout,
          Async[F].raiseError(TestFailedException(s"Could not consume $noMessages messages within $timeout", 0))
        )
    }
  }

  private def toResult[F[_] : Async, V](cr: CommittableConsumerRecord[F, String, V]): F[ConsumerResult[V]] =
    for {
      consumedAt <- Async[F].realTimeInstant
      producedAt <- {
        val ts = cr.record.timestamp
        (ts.createTime orElse ts.unknownTime orElse ts.logAppendTime)
          .map(Instant.ofEpochMilli.apply)
          .liftTo(TestFailedException("Record timestamp was empty", 0))
      }
    } yield ConsumerResult(
      topic = cr.record.topic,
      key = cr.record.key,
      value = cr.record.value,
      producedAt = producedAt,
      consumedAt = consumedAt,
      headers = cr.record.headers.toChain.toList.map(header => header.key -> header.as[String]).toMap
    )
}
