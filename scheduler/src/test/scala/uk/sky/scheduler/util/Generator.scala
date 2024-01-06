package uk.sky.scheduler.util

import java.time.Instant

import cats.effect.Sync
import cats.syntax.all.*
import monocle.syntax.all.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.exceptions.TestFailedException
import uk.sky.scheduler.domain.*
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.message.{Message, Metadata => MessageMetadata}
import uk.sky.scheduler.syntax.all.*

object Generator {
  given Arbitrary[Metadata] = Arbitrary(Gen.resultOf(Metadata.apply))
  given Arbitrary[Schedule] = Arbitrary(Gen.resultOf(Schedule.apply))

  given Arbitrary[MessageMetadata] = Arbitrary {
    for {
      raw <- Arbitrary.arbitrary[Map[String, String]]
    } yield MessageMetadata.fromMap(raw)
  }

  val scheduleEventArb: Gen[ScheduleEvent] = Gen.resultOf(ScheduleEvent.apply)
  given Arbitrary[ScheduleEvent]           = Arbitrary(scheduleEventArb)

  def generateSchedule[F[_] : Sync]: F[ScheduleEvent] =
    for {
      maybeSchedule <- Sync[F].delay(scheduleEventArb.sample)
      schedule      <- maybeSchedule.liftTo(TestFailedException("Could not generate a schedule", 0))
      now           <- Sync[F].epochMilli
    } yield schedule.focus(_.schedule.time).replace(now)

  def generateSchedule[F[_] : Sync](f: Instant => Instant): F[ScheduleEvent] =
    generateSchedule[F].map(_.focus(_.schedule.time).modify { l =>
      f(Instant.ofEpochMilli(l)).toEpochMilli
    })

  def generateSchedule[F[_] : Sync](time: Long): F[ScheduleEvent] =
    generateSchedule[F].map(_.focus(_.schedule.time).replace(time))

  private def message(
      key: String,
      source: String,
      scheduleEvent: Option[ScheduleEvent],
      metadata: MessageMetadata,
      expire: Boolean
  ): Message[Either[ScheduleError, Option[ScheduleEvent]]] = {
    val m = Message(
      key = key,
      source = source,
      value = scheduleEvent.asRight[ScheduleError],
      metadata = metadata
    )

    if (expire) m.expire else m
  }

  extension (scheduleEvent: ScheduleEvent) {
    def update(
        metadata: MessageMetadata = MessageMetadata.empty
    ): Message[Either[ScheduleError, Option[ScheduleEvent]]] =
      message(
        key = scheduleEvent.metadata.id,
        source = scheduleEvent.metadata.scheduleTopic,
        scheduleEvent = scheduleEvent.some,
        metadata = metadata,
        expire = false
      )

    def delete(
        metadata: MessageMetadata = MessageMetadata.empty,
        expire: Boolean = false
    ): Message[Either[ScheduleError, Option[ScheduleEvent]]] =
      message(
        key = scheduleEvent.metadata.id,
        source = scheduleEvent.metadata.scheduleTopic,
        scheduleEvent = none[ScheduleEvent],
        metadata = metadata,
        expire = expire
      )
  }
}
