package com.sky.kms.domain

import scala.concurrent.duration.FiniteDuration

case class ScheduleEvent(
    delay: FiniteDuration,
    inputTopic: String,
    outputTopic: String,
    key: Array[Byte],
    value: Option[Array[Byte]],
    headers: Map[String, Array[Byte]]
)
