package com.sky.kms.kafka

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.{CommittableOffset, CommittableOffsetBatch}
import akka.kafka.scaladsl.Consumer.Control
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.data.NonEmptyList
import cats.{Applicative, Comonad, Eval, Traverse}
import com.sky.kms.config.OffsetBatchConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer}

import scala.concurrent.Future
import scala.language.higherKinds

object KafkaStream {

  def source[T](topics: NonEmptyList[Topic])(implicit system: ActorSystem, crDecoder: ConsumerRecordDecoder[T]): Source[KafkaMessage[T], Control] =
    Consumer.committableSource(
      ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"),
      Subscriptions.topics(topics.map(_.value).toList.toSet)
    ).map(cm => KafkaMessage(cm.committableOffset, crDecoder(cm.record)))

  def commitOffset(bc: OffsetBatchConfig): Flow[KafkaMessage[_], Done, NotUsed] =
    Flow[KafkaMessage[_]]
      .map(_.offset)
      .groupedWithin(bc.commitBatchSize.value, bc.maxCommitWait)
      .map(CommittableOffsetBatch(_))
      .mapAsync(5)(_.commitScaladsl())

  def sink(implicit system: ActorSystem): Sink[ProducerRecord[Array[Byte], Array[Byte]], Future[Done]] =
    Producer.plainSink(ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer))
}

case class KafkaMessage[T](offset: CommittableOffset, value: T)

object KafkaMessage {

  implicit val catsInstances = new Traverse[KafkaMessage] with Comonad[KafkaMessage] {
    override def traverse[G[_], A, B](fa: KafkaMessage[A])(f: A => G[B])(implicit A: Applicative[G]): G[KafkaMessage[B]] = A.map(f(fa.value))(KafkaMessage(fa.offset, _))

    override def foldLeft[A, B](fa: KafkaMessage[A], b: B)(f: (B, A) => B): B = f(b, fa.value)

    override def foldRight[A, B](fa: KafkaMessage[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = f(fa.value, lb)

    override def extract[A](x: KafkaMessage[A]): A = x.value

    override def coflatMap[A, B](fa: KafkaMessage[A])(f: KafkaMessage[A] => B): KafkaMessage[B] = KafkaMessage(fa.offset, f(fa))
  }
}