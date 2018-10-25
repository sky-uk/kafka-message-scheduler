package com.sky.kms.base

import com.sky.kms.common.TestActorSystem
import com.sky.kms.config.SchedulerConfig
import net.manub.embeddedkafka.ConsumerExtensions._
import net.manub.embeddedkafka.Consumers
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.serialization.Deserializer
import net.manub.embeddedkafka.Codecs.{stringDeserializer, nullDeserializer}

abstract class SchedulerIntSpecBase
  extends AkkaStreamSpecBase
    with KafkaIntSpecBase
    with Consumers {

  val scheduleTopic = "scheduleTopic"
  val extraScheduleTopic = "extraScheduleTopic"

  override implicit lazy val system = TestActorSystem(kafkaConfig.kafkaPort)

<<<<<<< HEAD
  implicit val conf = SchedulerConfig(Set(scheduleTopic, extraScheduleTopic), queueBufferSize = 100)

  def consumeFirstFrom[T: Deserializer](topic: String): ConsumerRecord[Array[Byte], T] =
    withConsumer[Array[Byte], T, ConsumerRecord[Array[Byte], T]](
      _.consumeLazily(topic)(identity, ConsumerRetryConfig()).head)

  def consumeSomeFrom[T: Deserializer](topic: String, numMsgs: Int): List[ConsumerRecord[String, T]] =
    withConsumer { cr: KafkaConsumer[String, T] =>
      cr.consumeLazily(topic)(identity, ConsumerRetryConfig()).take(numMsgs).toList
    }
=======
  val conf = SchedulerConfig(Set(scheduleTopic, extraScheduleTopic), 100)
>>>>>>> resiliency tests
}
