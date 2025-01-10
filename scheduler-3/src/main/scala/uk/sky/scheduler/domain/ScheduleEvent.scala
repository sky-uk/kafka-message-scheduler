package uk.sky.scheduler.domain

/** A ScheduleEvent represents the metadata that created the Schedule, and the Schedule to be suspended.
  * @param metadata
  *   Information about the Schedule's source.
  * @param schedule
  *   The Schedule's destination and payload.
  */
case class ScheduleEvent(
    metadata: Metadata,
    schedule: Schedule
)

/** Information about a Schedule's source.
  * @param id
  *   The ID of the Schedule's message.
  * @param scheduleTopic
  *   The topic the Schedule arrived on.
  */
case class Metadata(
    id: String,
    scheduleTopic: String
)

/** The Schedule's destination and payload.
  *
  * @param time
  *   The time to execute the Schedule, in epoch milliseconds.
  * @param topic
  *   The topic to send the Schedule to.
  * @param key
  *   The key identifying the payload.
  * @param value
  *   The payload to be sent.
  * @param headers
  *   Extra metadata to send with the payload.
  */
case class Schedule(
    time: Long,
    topic: String,
    key: Array[Byte],
    value: Option[Array[Byte]],
    headers: Map[String, Array[Byte]]
)
