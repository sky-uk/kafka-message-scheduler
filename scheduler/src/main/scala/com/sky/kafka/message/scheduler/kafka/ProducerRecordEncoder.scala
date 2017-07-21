package com.sky.kafka.message.scheduler.kafka

import org.apache.kafka.clients.producer.ProducerRecord

trait ProducerRecordEncoder[T] {
  def apply(t: T): ProducerRecord[Array[Byte], Array[Byte]]
}
