package com.sky.kms.common

import cakesolutions.kafka.testkit.KafkaServer
import cakesolutions.kafka.testkit.KafkaServer.defaultConsumerConfig
import org.apache.kafka.clients.consumer.{ConsumerConfig, ConsumerRecord, KafkaConsumer}
import org.apache.kafka.common.serialization.Deserializer

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

object EmbeddedKafka {

  val kafkaServer = new KafkaServer

  val bootstrapServer = s"localhost:${kafkaServer.kafkaPort}"

  /** The consume method provided by [[cakesolutions.kafka.testkit.KafkaServer]] doesn't provide a way of
    * extracting a consumer record, we have added this so we can access the timestamp from a consumer record.
    *
    * This should be contributed to the library.
    *
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

      val extendedConfig: Map[String, Object] = consumerConfig + (ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> bootstrapServer)
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
