package com.sky.kafka.message.scheduler

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{RunnableGraph, Sink, Source}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization._

import scala.concurrent.Future

package object kafka {

  def consumeFromKafka[T](topic: String)(implicit system: ActorSystem, crDecoder: ConsumerRecordDecoder[T]): Source[T, Control] =
    Consumer.plainSource(
      ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer),
      Subscriptions.topics(topic)
    ).map(crDecoder(_))

  implicit class SourceOps[Out, Mat](source: Source[Out, Mat]) {
    def writeToKafka(implicit system: ActorSystem, recordEncoder: ProducerRecordEncoder[Out]): RunnableGraph[Mat] =
      source.map(recordEncoder(_)).to(kafkaSink)
  }

  private def kafkaSink(implicit system: ActorSystem): Sink[ProducerRecord[Array[Byte], Array[Byte]], Future[Done]] =
    Producer.plainSink(ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer))

}
