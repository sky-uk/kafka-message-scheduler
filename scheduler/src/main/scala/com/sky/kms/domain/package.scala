package com.sky.kms

import java.lang

import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader

import scala.jdk.CollectionConverters.*

package object domain {

  type ScheduleId = String

  implicit class HeadersOps(private val headers: Map[String, Array[Byte]]) extends AnyVal {
    def asKafkaHeaders: lang.Iterable[Header] = headers.map { case (k, v) => new RecordHeader(k, v): Header }.asJava
  }

}
