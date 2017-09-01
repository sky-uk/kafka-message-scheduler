package com.sky.kms.kafka

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Sink, Source}
import com.sky.kms.config.SchedulerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer}

import scala.concurrent.Future

object KafkaStream {

  def source[T](config: SchedulerConfig)(implicit system: ActorSystem, crDecoder: ConsumerRecordDecoder[T]): Source[T, Control] =
    Consumer.plainSource(
      ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer),
      Subscriptions.topics(config.scheduleTopic)
    ).map(crDecoder(_))

  def sink(implicit system: ActorSystem): Sink[ProducerRecord[Array[Byte], Array[Byte]], Future[Done]] =
    Producer.plainSink(ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer))

}
