package com.sky.kms

import java.lang
import java.time.OffsetDateTime

import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.header.{Header => KafkaHeader}

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

package object domain {

  type ScheduleId = String

  case class Schedule(time: OffsetDateTime, topic: String, key: Array[Byte], value: Option[Array[Byte]], headers: Set[Header])

  case class ScheduleEvent(delay: FiniteDuration, inputTopic: String, outputTopic: String, key: Array[Byte], value: Option[Array[Byte]], headers: Set[Header])

  case class Header(key: String, value: Array[Byte])


  implicit class HeadersOps(val headers: Set[Header]) extends AnyVal {
    def asKafkaHeaders: lang.Iterable[KafkaHeader] = headers.map(h => new RecordHeader(h.key, h.value): KafkaHeader)
      .toIterable
      .asJava
  }

}
