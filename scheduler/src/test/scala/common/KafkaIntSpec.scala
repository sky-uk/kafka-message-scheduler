package common

import EmbeddedKafka._
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, Deserializer, StringSerializer}
import org.scalatest.{BeforeAndAfterAll, Suite}

trait KafkaIntSpec extends BeforeAndAfterAll { this: Suite =>

  override def beforeAll() = kafkaServer.startup()

  override def afterAll() = kafkaServer.close()

  def writeToKafka(topic: String, key: String, value: Array[Byte]) {
    val producerRecord = new ProducerRecord[String, Array[Byte]](topic, key, value)
    kafkaServer.produce(topic, Iterable(producerRecord), new StringSerializer, new ByteArraySerializer)
  }

  def consumeFromKafka[T](topic: String, numRecords: Int = 1, keyDeserializer: Deserializer[T]): Seq[(Option[T], Array[Byte])] =
    kafkaServer.consume(topic, numRecords, 5000, keyDeserializer, new ByteArrayDeserializer)

}
