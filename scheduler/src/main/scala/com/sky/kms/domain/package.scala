package com.sky.kms

import java.lang

import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

package object domain {

  type ScheduleId = String

  case class ScheduleEvent(
      delay: FiniteDuration,
      inputTopic: String,
      outputTopic: String,
      key: Array[Byte],
      value: Option[Array[Byte]],
      headers: Map[String, Array[Byte]]
  )

  implicit class HeadersOps(private val headers: Map[String, Array[Byte]]) extends AnyVal {
    def asKafkaHeaders: lang.Iterable[Header] = headers.map { case (k, v) => new RecordHeader(k, v): Header }.asJava
  }

}
