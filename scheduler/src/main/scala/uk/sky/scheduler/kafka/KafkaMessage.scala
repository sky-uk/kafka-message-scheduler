package uk.sky.scheduler.kafka

// TODO make generic effect type
case class KafkaMessage[V](topic: String, value: V)
