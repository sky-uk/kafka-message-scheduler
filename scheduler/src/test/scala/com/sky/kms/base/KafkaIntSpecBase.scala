package com.sky.kms.base

import com.sky.kms.kafka.Topic
import com.sky.kms.utils.RandomPort.randomPort
import eu.timepit.refined.auto._
import io.github.embeddedkafka.Codecs.{nullDeserializer, stringDeserializer}
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.serialization.Deserializer

import scala.compat.java8.DurationConverters._
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

trait KafkaIntSpecBase extends EmbeddedKafka {

  implicit lazy val kafkaConfig: EmbeddedKafkaConfig =
    EmbeddedKafkaConfig(
      kafkaPort = randomPort(),
      zooKeeperPort = randomPort()
    )

  val scheduleTopic: Topic      = "scheduleTopic"
  val extraScheduleTopic: Topic = "extraScheduleTopic"
  val allTopics: List[Topic]    = List(scheduleTopic, extraScheduleTopic)

  def kafkaConsumerTimeout: FiniteDuration = 60.seconds

  private def subscribeAndPoll[K, V](topic: String): KafkaConsumer[K, V] => Iterator[ConsumerRecord[K, V]] = { cr =>
    cr.subscribe(List(topic).asJavaCollection)
    cr.poll(kafkaConsumerTimeout.toJava).iterator().asScala
  }

  def consumeFirstFrom[T : Deserializer](topic: String): ConsumerRecord[Array[Byte], T] =
    withConsumer { cr: KafkaConsumer[Array[Byte], T] =>
      subscribeAndPoll(topic)(cr).next()
    }

  def consumeSomeFrom[T : Deserializer](topic: String, numMsgs: Int): List[ConsumerRecord[String, T]] =
    withConsumer { cr: KafkaConsumer[String, T] =>
      subscribeAndPoll(topic)(cr).toList.take(numMsgs)
    }
}
