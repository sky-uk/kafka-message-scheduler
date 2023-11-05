package uk.sky.scheduler.kafka

case class Message[V](key: String, topic: String, value: V)
