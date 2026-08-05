package uk.sky.scheduler.util

import cats.syntax.all.*
import fs2.kafka.ProducerRecord
import org.scalatest.matchers.{MatchResult, Matcher}
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.message.Message

private class AvroScheduleMatcher(right: AvroSchedule) extends Matcher[AvroSchedule] {
  override def apply(left: AvroSchedule): MatchResult =
    MatchResult(
      left === right,
      s"${left.show} did not equal ${right.show}",
      s"${left.show} equals ${right.show}"
    )
}

private class JsonScheduleMatcher(right: JsonSchedule) extends Matcher[JsonSchedule] {
  override def apply(left: JsonSchedule): MatchResult =
    MatchResult(
      left === right,
      s"${left.show} did not equal ${right.show}",
      s"${left.show} equals ${right.show}"
    )
}

private class ProducerRecordMatcher(right: ProducerRecord[Array[Byte], Option[Array[Byte]]])
    extends Matcher[ProducerRecord[Array[Byte], Option[Array[Byte]]]] {
  override def apply(left: ProducerRecord[Array[Byte], Option[Array[Byte]]]): MatchResult =
    MatchResult(
      left === right,
      s"${left.show} did not equal ${right.show}",
      s"${left.show} equals ${right.show}"
    )
}

private class ScheduleEventMatcher(right: ScheduleEvent) extends Matcher[ScheduleEvent] {
  override def apply(left: ScheduleEvent): MatchResult =
    MatchResult(
      left === right,
      s"${left.show} did not equal ${right.show}",
      s"${left.show} equals ${right.show}"
    )
}

private class MessageMatcher(right: Message[Either[ScheduleError, Option[ScheduleEvent]]])
    extends Matcher[Message[Either[ScheduleError, Option[ScheduleEvent]]]] {
  override def apply(left: Message[Either[ScheduleError, Option[ScheduleEvent]]]): MatchResult =
    MatchResult(
      left === right,
      s"${left.show} did not equal ${right.show}",
      s"${left.show} equals ${right.show}"
    )
}

/** We deal with `Array[Byte]` which doesn't support universal equals. To get around this, we can use a custom Eq to
  * convert to another data type and assert they are equal. Below are some convenience traits to mix in to tests.
  */

trait ScheduleMatchers {
  def equalSchedule(expectedSchedule: AvroSchedule): AvroScheduleMatcher = AvroScheduleMatcher(expectedSchedule)

  def equalSchedule(expectedSchedule: JsonSchedule): JsonScheduleMatcher = JsonScheduleMatcher(expectedSchedule)

  def equalSchedule(expectedSchedule: ScheduleEvent): ScheduleEventMatcher = ScheduleEventMatcher(expectedSchedule)
}

trait ProducerRecordMatchers {
  def equalProducerRecord(
      expectedProducerRecord: ProducerRecord[Array[Byte], Option[Array[Byte]]]
  ): ProducerRecordMatcher = ProducerRecordMatcher(expectedProducerRecord)
}

trait MessageMatchers {
  def equalMessage(expectedMessage: Message[Either[ScheduleError, Option[ScheduleEvent]]]): MessageMatcher =
    MessageMatcher(expectedMessage)
}
