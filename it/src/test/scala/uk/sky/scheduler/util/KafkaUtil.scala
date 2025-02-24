package uk.sky.scheduler.util

import cats.effect.Async
import cats.effect.syntax.all.*
import cats.syntax.all.*
import fs2.{kafka, *}
import fs2.kafka.KafkaProducer
import fs2.kafka.*
import org.apache.kafka.common.TopicPartition
import org.scalatest.exceptions.TestFailedException
import uk.sky.scheduler.util.KafkaUtil.ConsumerResult

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

trait KafkaUtil[F[_]] {
  def produce[V](topic: String, keyValues: (String, Option[V])*)(using ValueSerializer[F, V]): F[Unit]

  def consume[V](topic: String, noMessages: Int, autoCommit: Boolean = true)(using ValueDeserializer[F, V]): F[List[ConsumerResult[V]]]

  def consumeLast[V](topic: String, noMessages: Int, autoCommit: Boolean = true)(using ValueDeserializer[F, V]): F[List[ConsumerResult[V]]]
}

object KafkaUtil {
  final case class ConsumerResult[V](topic: String, key:String, value: V, producedAt: Instant, consumedAt: Instant, headers: Map[String, String]) {
    val keyValue :(String, V) = key -> value
  }

  def apply[F[_]: Async](kafkaPort: Int, timeoutException: FiniteDuration): KafkaUtil[F] = new KafkaUtil[F] {
    
    override def produce[V](topic: String, keyValues: (String, Option[V])*)(using ValueSerializer[F, V]): F[Unit] = {
      val producerSettings = ProducerSettings[F, String, V]
        .withBootstrapServers(s"localhost:$kafkaPort")
      val records = Chunk.from(keyValues).map(ProducerRecord(topic, _, _))
      
      KafkaProducer
        .stream(producerSettings)
        .evalMap(_.produce(records))
        .parEvalMapUnbounded(identity)
        .compile
        .drain
        
//      KafkaProducer
//        .stream(producerSettings)
//        .evalTap(_.produce(records))
//        .parEvalMapUnbounded(identity)
//        .compile
//        .drain  
      s
    }
      

    override def consume[V](topic: String, noMessages: Int, autoCommit: Boolean)(using ValueDeserializer[F, V]): F[List[ConsumerResult[V]]] = ???

    override def consumeLast[V](topic: String, noMessages: Int, autoCommit: Boolean)(using ValueDeserializer[F, V]): F[List[ConsumerResult[V]]] = ???
  }
}
