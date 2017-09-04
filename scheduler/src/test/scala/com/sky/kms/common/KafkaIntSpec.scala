package com.sky.kms.common

import cakesolutions.kafka.testkit.KafkaServer
import com.sky.kms.common.EmbeddedKafka._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, Deserializer, StringSerializer}
import org.scalatest.{BeforeAndAfterAll, Suite}

trait KafkaIntSpec extends BeforeAndAfterAll { this: Suite =>

  lazy val kafkaServer = new KafkaServer

  val bootstrapServer = s"localhost:${kafkaServer.kafkaPort}"

  val zkServer = s"localhost:${kafkaServer.zookeeperPort}"

  override def beforeAll() {
    kafkaServer.startup()
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
    kafkaServer.close()
  }

  def writeToKafka(topic: String, keyValues: (String, Array[Byte])*) {
    val producerRecords = keyValues.map { case (key, value) => new ProducerRecord[String, Array[Byte]](topic, key, value) }
    kafkaServer.produce(topic, producerRecords, new StringSerializer, new ByteArraySerializer)
  }

  def consumerRecordConverter[T]: ConsumerRecord[T, Array[Byte]] => ConsumerRecord[T, Array[Byte]] = identity

  def consumeFromKafka[T](topic: String, numRecords: Int = 1, keyDeserializer: Deserializer[T]): Seq[ConsumerRecord[T, Array[Byte]]] =
    kafkaServer.consumeRecord(topic, numRecords, 5000, keyDeserializer, new ByteArrayDeserializer, consumerRecordConverter[T])

}
