package uk.sky.scheduler.converters

import cats.syntax.all.*
import fs2.kafka.{ConsumerRecord, Header, Headers}
import io.scalaland.chimney.Transformer
import io.scalaland.chimney.dsl.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{EitherValues, LoneElement}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import org.typelevel.ci.CIString
import uk.sky.scheduler.domain.{Schedule, ScheduleEvent}
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.message.{Message, Metadata}
import uk.sky.scheduler.util.Generator.given
import uk.sky.scheduler.util.MessageMatchers

class ConsumerRecordConverterSpec
    extends AnyWordSpec,
      Matchers,
      ScalaCheckPropertyChecks,
      MessageMatchers,
      EitherValues,
      LoneElement,
      ConsumerRecordConverter {

  given Arbitrary[ScheduleError] = Arbitrary(Gen.const(ScheduleError.DecodeError("foo", Throwable())))

  "ConsumerRecordConverter" should {
    "transform an update into a Message" in forAll { (scheduleEvent: ScheduleEvent) =>
      val jsonSchedule: JsonSchedule =
        scheduleEvent.schedule.transformInto[JsonSchedule]
      val avroSchedule: Schedule     =
        scheduleEvent.schedule.into[Schedule].transform

      forAll(Table("schedule", jsonSchedule, avroSchedule)) { (schedule: JsonSchedule | Schedule) =>
        val consumerRecord = ConsumerRecord[String, Either[ScheduleError, Option[JsonSchedule | Schedule]]](
          topic = scheduleEvent.metadata.scheduleTopic,
          partition = 0,
          offset = 0L,
          key = scheduleEvent.metadata.id,
          value = schedule.some.asRight[ScheduleError]
        ).withHeaders(Headers.fromIterable(scheduleEvent.schedule.headers.map(Header.apply)))

        val message = Message[Either[ScheduleError, Option[ScheduleEvent]]](
          key = consumerRecord.key,
          source = consumerRecord.topic,
          value = scheduleEvent.some.asRight[ScheduleError],
          metadata = Metadata(scheduleEvent.schedule.headers.map(CIString(_) -> String(_)))
        )

        consumerRecord.toMessage should equalMessage(message)
      }
    }

    "transform invalid Base64 into an error Message" in forAll { (scheduleEvent: ScheduleEvent) =>
      val jsonSchedule: JsonSchedule =
        scheduleEvent.schedule.transformInto[JsonSchedule].copy(key = "invalid-base64")

      val consumerRecord = ConsumerRecord[String, Either[ScheduleError, Option[JsonSchedule | Schedule]]](
        topic = scheduleEvent.metadata.scheduleTopic,
        partition = 0,
        offset = 0L,
        key = scheduleEvent.metadata.id,
        value = jsonSchedule.some.asRight[ScheduleError]
      )

      consumerRecord.toMessage.value.left.value shouldBe a[ScheduleError.TransformationError]
    }

    "transform a delete into a Message" in forAll { (scheduleEvent: ScheduleEvent) =>
      val consumerRecord = ConsumerRecord[String, Either[ScheduleError, Option[JsonSchedule | Schedule]]](
        topic = scheduleEvent.metadata.scheduleTopic,
        partition = 0,
        offset = 0L,
        key = scheduleEvent.metadata.id,
        value = none[JsonSchedule | Schedule].asRight[ScheduleError]
      ).withHeaders(Headers.fromIterable(scheduleEvent.schedule.headers.map(Header.apply)))

      val message = Message[Either[ScheduleError, Option[ScheduleEvent]]](
        key = consumerRecord.key,
        source = consumerRecord.topic,
        value = none[ScheduleEvent].asRight[ScheduleError],
        metadata = Metadata(scheduleEvent.schedule.headers.map(CIString(_) -> String(_)))
      )

      consumerRecord.toMessage should equalMessage(message)
    }

    "transform an error into a Message" in forAll { (scheduleEvent: ScheduleEvent, scheduleError: ScheduleError) =>
      val consumerRecord = ConsumerRecord[String, Either[ScheduleError, Option[JsonSchedule | Schedule]]](
        topic = scheduleEvent.metadata.scheduleTopic,
        partition = 0,
        offset = 0L,
        key = scheduleEvent.metadata.id,
        value = scheduleError.asLeft[Option[JsonSchedule | Schedule]]
      ).withHeaders(Headers.fromIterable(scheduleEvent.schedule.headers.map(Header.apply)))

      val decodeError = ScheduleError.DecodeError(scheduleEvent.metadata.id, scheduleError)
      val message     = Message[Either[ScheduleError, Option[ScheduleEvent]]](
        key = consumerRecord.key,
        source = consumerRecord.topic,
        value = decodeError.asLeft[Option[ScheduleEvent]],
        metadata = Metadata(scheduleEvent.schedule.headers.map(CIString(_) -> String(_)))
      )

      consumerRecord.toMessage should equalMessage(message)
    }

    "drop null header values" in {
      val nonNullHeaders = Headers(Header("foo", "bar"))
      val nullHeaders    = Headers(Header("bee", none[String]))
      val consumerRecord = ConsumerRecord(
        topic = "topic",
        partition = 0,
        offset = 0L,
        key = "key",
        value = none[JsonSchedule | Schedule].asRight[ScheduleError]
      ).withHeaders(nonNullHeaders concat nullHeaders)

      consumerRecord.toMessage.metadata.value should contain only (CIString("foo") -> "bar")
    }

  }

}
