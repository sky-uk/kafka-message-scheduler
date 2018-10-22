package com.sky.kms.kafka

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.CommittableOffset
import akka.kafka.scaladsl.Consumer.Control
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer}

import scala.concurrent.Future

object KafkaStream {

  def source[T](topics: Set[Topic])(implicit system: ActorSystem, crDecoder: ConsumerRecordDecoder[T]): Source[KafkaMessage[T], Control] =
    Consumer.committableSource(
      ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer),
      Subscriptions.topics(topics.map(_.value))
    ).map(cm => KafkaMessage(cm.committableOffset, crDecoder(cm.record)))

  def commitOffset: Flow[KafkaMessage[_], Done, NotUsed] =
    Flow[KafkaMessage[_]]
      .mapAsync(5)(_.offset.commitScaladsl())

  def sink(implicit system: ActorSystem): Sink[ProducerRecord[Array[Byte], Array[Byte]], Future[Done]] =
    Producer.plainSink(ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer))
}

case class KafkaMessage[T](offset: CommittableOffset, value: T)
