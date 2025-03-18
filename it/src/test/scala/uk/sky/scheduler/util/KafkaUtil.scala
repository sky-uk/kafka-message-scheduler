package uk.sky.scheduler.util

import cats.effect.Async
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.*
import fs2.kafka.*
import org.apache.kafka.common.TopicPartition
import org.scalatest.exceptions.TestFailedException
import uk.sky.scheduler.util.KafkaUtil.ConsumerResult

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

trait KafkaUtil[F[_]] {
  def produce[V](topic: String, keyValues: (String, Option[V])*)(using ValueSerializer[F, V]): F[Unit]

  def consume[V](topic: String, noMessages: Int, autoCommit: Boolean = true)(using
      ValueDeserializer[F, V]
  ): F[List[ConsumerResult[V]]]

  def consumeLast[V](topic: String, noMessages: Int, autoCommit: Boolean = true)(using
      ValueDeserializer[F, V]
  ): F[List[ConsumerResult[V]]]
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

  def apply[F[_] : Async](bootstrapServer: String, timeout: FiniteDuration, consumerGroup: String): KafkaUtil[F] =
    new KafkaUtil[F] {

      override def produce[V](topic: String, keyValues: (String, Option[V])*)(using ValueSerializer[F, V]): F[Unit] = {
        val producerSettings = ProducerSettings[F, String, Option[V]]
          .withBootstrapServers(bootstrapServer)

        val records = Chunk.from(keyValues).map(ProducerRecord(topic, _, _))

        KafkaProducer
          .stream(producerSettings)
          .evalMapChunk(_.produce(records))
          .parEvalMapUnbounded(identity)
          .compile
          .drain
      }

      def consumerSettings[V](autoCommit: Boolean)(using
          ValueDeserializer[F, V]
      ) = ConsumerSettings[F, String, V]
        .withBootstrapServers(bootstrapServer)
        .withGroupId(consumerGroup)
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withEnableAutoCommit(autoCommit)

      override def consume[V](topic: String, noMessages: Int, autoCommit: Boolean)(using
          ValueDeserializer[F, V]
      ): F[List[ConsumerResult[V]]] =
        KafkaConsumer
          .stream(consumerSettings[V](autoCommit))
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

      override def consumeLast[V](topic: String, noMessages: Int, autoCommit: Boolean)(using
          ValueDeserializer[F, V]
      ): F[List[ConsumerResult[V]]] =
        KafkaConsumer
          .stream(consumerSettings[V](autoCommit))
          .evalTap { consumer =>
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
