package uk.sky.scheduler.kafka.json

final case class JsonSchedule(
    time: Long,
    topic: String,
    key: String,
    value: Option[String],
    headers: Map[String, String]
)
