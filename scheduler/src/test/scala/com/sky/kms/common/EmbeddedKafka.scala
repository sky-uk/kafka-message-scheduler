package com.sky.kms.common

import cakesolutions.kafka.testkit.KafkaServer
import cakesolutions.kafka.testkit.KafkaServer.defaultConsumerConfig
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, KafkaConsumer}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, ByteArraySerializer, Deserializer, StringSerializer}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

trait EmbeddedKafka {

  import EmbeddedKafka._

  lazy val kafkaServer = new KafkaServer

  lazy val zkServer = s"localhost:${kafkaServer.zookeeperPort}"

  def writeToKafka(topic: String, keyValues: (String, Array[Byte])*) {
    val producerRecords = keyValues.map { case (key, value) => new ProducerRecord[String, Array[Byte]](topic, key, value) }
    kafkaServer.produce(topic, producerRecords, new StringSerializer, new ByteArraySerializer)
  }

  def consumerRecordConverter[T]: ConsumerRecord[T, Array[Byte]] => ConsumerRecord[T, Array[Byte]] = identity

  def consumeFromKafka[T](topic: String, numRecords: Int = 1, keyDeserializer: Deserializer[T]): Seq[ConsumerRecord[T, Array[Byte]]] =
    kafkaServer.consumeRecord(topic, numRecords, timeout = 5000, keyDeserializer, new ByteArrayDeserializer, consumerRecordConverter[T])
}

object EmbeddedKafka {

  /**
    * The consume method provided by [[cakesolutions.kafka.testkit.KafkaServer]] doesn't provide a way of
    * extracting a consumer record, we have added this so we can access the timestamp from a consumer record. In the
    * future we should contribute this to the library as it provides better flexibility.
    */
  implicit class KafkaServerOps(val kafkaServer: KafkaServer) extends AnyVal {

    def consumeRecord[Key, Value, T](
                                topic: String,
                                expectedNumOfRecords: Int,
                                timeout: Long,
                                keyDeserializer: Deserializer[Key],
                                valueDeserializer: Deserializer[Value],
                                consumerRecordConverter: ConsumerRecord[Key, Value] => T,
                                consumerConfig: Map[String, String] = defaultConsumerConfig
                              ): Seq[T] = {

      val extendedConfig: Map[String, Object] = consumerConfig + (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> s"localhost:${kafkaServer.kafkaPort}")
      val consumer = new KafkaConsumer(extendedConfig.asJava, keyDeserializer, valueDeserializer)

      try {
        consumer.subscribe(List(topic).asJava)

        var total = 0
        val collected = ArrayBuffer.empty[T]
        val start = System.currentTimeMillis()

        while (total <= expectedNumOfRecords && System.currentTimeMillis() < start + timeout) {
          val records = consumer.poll(100)
          val kvs = records.asScala.map(consumerRecordConverter)
          collected ++= kvs
          total += records.count()
        }

        if (collected.size < expectedNumOfRecords) {
          sys.error(s"Did not receive expected amount records. Expected $expectedNumOfRecords but got ${collected.size}.")
        }

        collected.toVector
      } finally {
        consumer.close()
      }
    }

  }

}
