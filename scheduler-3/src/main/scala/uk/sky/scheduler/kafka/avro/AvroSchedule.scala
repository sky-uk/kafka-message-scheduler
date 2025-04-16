package uk.sky.scheduler.kafka.avro

import vulcan.generic.AvroDoc

final case class AvroSchedule(
    @AvroDoc("The time to execute the Schedule, in epoch milliseconds.")
    time: Long,
    @AvroDoc("The topic to send the Schedule to.")
    topic: String,
    @AvroDoc("The key identifying the payload.")
    key: Array[Byte],
    @AvroDoc("The payload to be sent. null indicates a tombstone.")
    value: Option[Array[Byte]],
    @AvroDoc("Optional extra metadata to send with the payload.")
    headers: Map[String, Array[Byte]]
)

final case class AvroScheduleWithoutHeaders(
    @AvroDoc("The time to execute the Schedule, in epoch milliseconds.")
    time: Long,
    @AvroDoc("The topic to send the Schedule to.")
    topic: String,
    @AvroDoc("The key identifying the payload.")
    key: Array[Byte],
    @AvroDoc("The payload to be sent. null indicates a tombstone.")
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
