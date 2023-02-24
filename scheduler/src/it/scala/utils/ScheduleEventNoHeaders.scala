package utils

import scala.concurrent.duration.FiniteDuration

case class ScheduleEventNoHeaders(
    delay: FiniteDuration,
    inputTopic: String,
    outputTopic: String,
    key: Array[Byte],
    value: Option[Array[Byte]]
)
