package uk.sky.scheduler.kafka.avro

final case class AvroSchedule(
    time: Long,
    topic: String,
    key: Array[Byte],
    value: Option[Array[Byte]],
    headers: Map[String, Array[Byte]]
)