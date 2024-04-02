package com.sky.kms.base

import com.sky.kms.kafka.Topic
import com.sky.kms.utils.RandomPort.randomPort
import eu.timepit.refined.auto.*
import io.github.embeddedkafka.Codecs.{nullDeserializer, stringDeserializer}
import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.serialization.Deserializer
import org.scalatest.wordspec.AnyWordSpec

import scala.compat.java8.DurationConverters.*
import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

trait KafkaIntSpecBase extends AnyWordSpec with EmbeddedKafka {

  implicit lazy val kafkaConfig: EmbeddedKafkaConfig =
    EmbeddedKafkaConfig(
      kafkaPort = randomPort(),
      zooKeeperPort = randomPort()
    )

  val scheduleTopic: Topic                 = "scheduleTopic"
  val extraScheduleTopic: Topic            = "extraScheduleTopic"
  def kafkaConsumerTimeout: FiniteDuration = 120.seconds

  private def subscribeAndPoll[K, V](topic: String): KafkaConsumer[K, V] => Iterator[ConsumerRecord[K, V]] = { cr =>
    cr.subscribe(List(topic).asJavaCollection)
    cr.poll(kafkaConsumerTimeout.toJava).iterator().asScala
  }

  def consumeFirstFrom[T : Deserializer](topic: String): ConsumerRecord[Array[Byte], T] = {
    val c = implicitly[EmbeddedKafkaConfig]
    println(s">>> ConsumeFirstFrom: $topic || Config: $c")
    withConsumer { cr: KafkaConsumer[Array[Byte], T] =>
      println(s">>> withConsumer: ${cr.listTopics()} || Subscribe and Pool from: $topic")
      subscribeAndPoll(topic)(cr).next()
    }
  }

  def consumeSomeFrom[T : Deserializer](topic: String, numMsgs: Int): List[ConsumerRecord[String, T]] =
    withConsumer { cr: KafkaConsumer[String, T] =>
      subscribeAndPoll(topic)(cr).toList.take(numMsgs)
    }
}
