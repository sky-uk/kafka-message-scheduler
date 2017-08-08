package com.sky.kms.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord

trait ConsumerRecordDecoder[T] {
  def apply(cr: ConsumerRecord[String, Array[Byte]]): T
}

object ConsumerRecordDecoder {
  def instance[T](f: ConsumerRecord[String, Array[Byte]] => T) = new ConsumerRecordDecoder[T] {
    final def apply(cr: ConsumerRecord[String, Array[Byte]]): T =
      f(cr)
  }
}
