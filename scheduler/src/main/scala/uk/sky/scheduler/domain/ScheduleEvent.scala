package uk.sky.scheduler.domain

case class ScheduleEvent(
    time: Long,
    topic: String,
    key: Array[Byte],
    value: Option[Array[Byte]],
    headers: Map[String, Array[Byte]]
)
