package com.sky.kms.kafka

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.scaladsl.{Sink, Source}
import com.sky.kms.config.SchedulerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, StringDeserializer}

import scala.collection.JavaConverters._
import scala.concurrent.Future

object KafkaStream {

  type Offset = Long

  def beginningOffsetSource[T](config: SchedulerConfig)(implicit system: ActorSystem, crDecoder: ConsumerRecordDecoder[T]): Source[T, Control] = {
    val consumerSettings = ConsumerSettings(system, new StringDeserializer, new ByteArrayDeserializer)
    Consumer.plainSource(
      consumerSettings,
      Subscriptions.assignmentWithOffset(beginningPartitionPositions(config.scheduleTopic, consumerSettings))
    ).map(crDecoder(_))
  }

  private def beginningPartitionPositions(topic: String, settings: ConsumerSettings[String, Array[Byte]]): Map[TopicPartition, Offset] = {
    val consumer = settings.createKafkaConsumer()

    consumer.subscribe(List(topic).asJava)
    consumer.poll(1)

    val partitions = consumer.assignment
    consumer.seekToBeginning(partitions)
    val partitionPositions = partitions.asScala.map(p => p -> consumer.position(p)).toMap

    consumer.close()
    partitionPositions
  }

  def sink(implicit system: ActorSystem): Sink[ProducerRecord[Array[Byte], Array[Byte]], Future[Done]] =
    Producer.plainSink(ProducerSettings(system, new ByteArraySerializer, new ByteArraySerializer))
}
