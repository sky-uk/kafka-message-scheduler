package uk.sky.scheduler.kafka.avro

final case class AvroSchedule(
    time: Long,
    topic: String,
    key: Array[Byte],
    value: Option[Array[Byte]],
    headers: Map[String, Array[Byte]]
)

final case class AvroScheduleWithoutHeaders(
    time: Long,
    topic: String,
    key: Array[Byte],
    value: Option[Array[Byte]]
) {
  val avroSchedule: AvroSchedule = AvroSchedule(
    time = time,
    topic = topic,
    key = key,
    value = value,
    headers = Map.empty
  )
}
