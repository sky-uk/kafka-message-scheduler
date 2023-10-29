package uk.sky.scheduler.domain

final case class Schedule(
    time: Long,
    topic: String,
    key: String,
    value: Option[String],
    headers: Map[String, String]
)
