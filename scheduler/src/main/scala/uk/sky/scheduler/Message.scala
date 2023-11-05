package uk.sky.scheduler

case class Message[V](key: String, source: String, value: V)
