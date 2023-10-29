package uk.sky.scheduler

import java.util.Base64

import cats.effect.kernel.Sync
import cats.syntax.all.*
import fs2.kafka.{Header, Headers, ProducerRecord}
import uk.sky.scheduler.domain.Schedule

case class ScheduleEvent(
    time: Long,
    topic: String,
    key: Array[Byte],
    value: Option[Array[Byte]],
    headers: Map[String, Array[Byte]]
)

object ScheduleEvent {
  extension (s: String) {
    private def base64Decode[F[_] : Sync]: F[Array[Byte]] = Sync[F].delay {
      Base64.getDecoder.decode(s)
    }
  }

  extension (bytes: Array[Byte]) {
    private def base64Encode[F[_] : Sync]: F[String] = Sync[F].delay {
      Base64.getEncoder.encodeToString(bytes)
    }
  }

  def fromSchedule[F[_] : Sync](schedule: Schedule): F[ScheduleEvent] =
    for {
      key     <- schedule.key.base64Decode
      value   <- schedule.value.traverse(_.base64Decode)
      headers <- schedule.headers.toList.traverse((key, value) => value.base64Decode.map(key -> _)).map(_.toMap)
    } yield ScheduleEvent(
      time = schedule.time,
      topic = schedule.topic,
      key = key,
      value = value,
      headers = headers
    )

  def toSchedule[F[_] : Sync](scheduleEvent: ScheduleEvent): F[Schedule] =
    for {
      key     <- scheduleEvent.key.base64Encode
      value   <- scheduleEvent.value.traverse(_.base64Encode)
      headers <- scheduleEvent.headers.toList.traverse((key, value) => value.base64Encode.map(key -> _)).map(_.toMap)
    } yield Schedule(
      time = scheduleEvent.time,
      topic = scheduleEvent.topic,
      key = key,
      value = value,
      headers = headers
    )

  def toProducerRecord(scheduleEvent: ScheduleEvent): ProducerRecord[Array[Byte], Option[Array[Byte]]] =
    ProducerRecord(
      topic = scheduleEvent.topic,
      key = scheduleEvent.key,
      value = scheduleEvent.value
    ).withHeaders(Headers.fromSeq(scheduleEvent.headers.toSeq.map(Header.apply)))
}
