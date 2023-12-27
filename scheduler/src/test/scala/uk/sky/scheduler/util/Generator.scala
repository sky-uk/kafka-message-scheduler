package uk.sky.scheduler.util

import java.time.Instant

import cats.effect.Sync
import cats.syntax.all.*
import monocle.syntax.all.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.exceptions.TestFailedException
import uk.sky.scheduler.Message
import uk.sky.scheduler.Message.Headers
import uk.sky.scheduler.domain.*
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.util.testSyntax.*

object Generator {
  given Arbitrary[Metadata] = Arbitrary(Gen.resultOf(Metadata.apply))
  given Arbitrary[Schedule] = Arbitrary(Gen.resultOf(Schedule.apply))

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
      headers: Headers,
      expire: Boolean
  ): Message[Either[ScheduleError, Option[ScheduleEvent]]] = {
    val m = Message(
      key = key,
      source = source,
      value = scheduleEvent.asRight[ScheduleError],
      headers = headers
    )

    if (expire) m.expire else m
  }

  extension (scheduleEvent: ScheduleEvent) {
    def update(headers: Headers = Headers.empty): Message[Either[ScheduleError, Option[ScheduleEvent]]] =
      message(
        key = scheduleEvent.metadata.id,
        source = scheduleEvent.metadata.scheduleTopic,
        scheduleEvent = scheduleEvent.some,
        headers = headers,
        expire = false
      )

    def delete(
        headers: Headers = Headers.empty,
        expire: Boolean = false
    ): Message[Either[ScheduleError, Option[ScheduleEvent]]] =
      message(
        key = scheduleEvent.metadata.id,
        source = scheduleEvent.metadata.scheduleTopic,
        scheduleEvent = none[ScheduleEvent],
        headers = headers,
        expire = expire
      )
  }
}
