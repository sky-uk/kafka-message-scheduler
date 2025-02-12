package uk.sky.scheduler.kafka.avro

final case class AvroSchedule(
  time: Long,
  topic: String,
  key: Array[Byte],
  value: Option[Array[Byte]],
  optionalHeaders: Option[Map[String, Array[Byte]]]
) {
  val headers: Map[String, Array[Byte]] = optionalHeaders.getOrElse(Map.empty[String, Array[Byte]])
}
