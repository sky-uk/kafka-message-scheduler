package utils

import io.github.embeddedkafka.EmbeddedKafka.withConsumer
import io.github.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import io.github.embeddedkafka.Codecs.{nullDeserializer, stringDeserializer}
import org.apache.kafka.common.serialization.Deserializer

import scala.compat.java8.DurationConverters._
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

trait KafkaUtils {
  implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = 9093)
  val scheduleTopic                             = "schedules"
  val extraScheduleTopic                        = "extraSchedules"
  val outputTopic                               = "output.topic"
  val kafkaConsumerTimeout: FiniteDuration      = 60.seconds
  val tolerance: FiniteDuration                 = 1300.milliseconds

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
