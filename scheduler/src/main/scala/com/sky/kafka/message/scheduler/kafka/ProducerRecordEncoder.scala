package com.sky.kafka.message.scheduler.kafka

import org.apache.kafka.clients.producer.ProducerRecord

trait ProducerRecordEncoder[T] {
  def apply(t: T): ProducerRecord[Array[Byte], Array[Byte]]
}

object ProducerRecordEncoder {

  def instance[A, B](f: A => ProducerRecord[Array[Byte], Array[Byte]]) =
    new ProducerRecordEncoder[A]() {
      final def apply(a: A): ProducerRecord[Array[Byte], Array[Byte]] = f(a)
    }
}
