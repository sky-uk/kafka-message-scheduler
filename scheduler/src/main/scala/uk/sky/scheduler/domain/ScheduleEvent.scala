package uk.sky.scheduler.domain

case class ScheduleEvent(
    metadata: Metadata,
    schedule: Schedule
)

case class Metadata(
    id: String,
    scheduleTopic: String
)

case class Schedule(
    time: Long,
    topic: String,
    key: Array[Byte],
    value: Option[Array[Byte]],
    headers: Map[String, Array[Byte]]
)
