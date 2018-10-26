package com.sky.kms.base

import com.sky.kms.kafka.Topic
import com.sky.kms.utils.RandomPort.randomPort
import eu.timepit.refined.auto._
import net.manub.embeddedkafka.Codecs.{nullDeserializer, stringDeserializer}
import net.manub.embeddedkafka.ConsumerExtensions.{ConsumerRetryConfig, _}
import net.manub.embeddedkafka.{Consumers, EmbeddedKafka, EmbeddedKafkaConfig}
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.serialization.Deserializer
import org.scalatest.WordSpecLike

trait KafkaIntSpecBase extends EmbeddedKafka with WordSpecLike with Consumers {

  implicit lazy val kafkaConfig = EmbeddedKafkaConfig(kafkaPort = randomPort(), zooKeeperPort = randomPort())

  val scheduleTopic: Topic = "scheduleTopic"
  val extraScheduleTopic: Topic = "extraScheduleTopic"

  val retryConfig = ConsumerRetryConfig(maximumAttempts = 50)

  def consumeFirstFrom[T: Deserializer](topic: String): ConsumerRecord[Array[Byte], T] =
    withConsumer[Array[Byte], T, ConsumerRecord[Array[Byte], T]](
      _.consumeLazily(topic)(identity, retryConfig).head)

  def consumeSomeFrom[T: Deserializer](topic: String, numMsgs: Int): List[ConsumerRecord[String, T]] =
    withConsumer { cr: KafkaConsumer[String, T] =>
      cr.consumeLazily(topic)(identity, retryConfig).take(numMsgs).toList
    }
}
