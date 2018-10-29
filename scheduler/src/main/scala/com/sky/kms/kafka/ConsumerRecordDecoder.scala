package com.sky.kms.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord

trait ConsumerRecordDecoder[T] {
  def apply(cr: ConsumerRecord[String, Array[Byte]]): T
}
