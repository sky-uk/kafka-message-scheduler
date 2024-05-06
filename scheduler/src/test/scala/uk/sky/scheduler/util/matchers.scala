package uk.sky.scheduler.util

import cats.syntax.all.*
import cats.{Eq, Show}
import fs2.kafka.ProducerRecord
import org.scalatest.matchers.{MatchResult, Matcher}
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule

private given Show[AvroSchedule] = Show.show { schedule =>
  s"AvroSchedule(time=${schedule.time}, topic=${schedule.topic}, key=${schedule.key.toList}, value=${schedule.value
      .map(_.toList)}, headers=${schedule.headers.view.mapValues(_.toList).toMap})"
}

private given Show[JsonSchedule] = Show.show { schedule =>
  s"AvroSchedule(time=${schedule.time}, topic=${schedule.topic}, key=${schedule.key}, value=${schedule.value}, headers=${schedule.headers})"
}

private class AvroScheduleMatcher(right: AvroSchedule) extends Matcher[AvroSchedule] {
  override def apply(left: AvroSchedule): MatchResult =
    MatchResult(
      left.time === right.time &&
        left.topic === right.topic &&
        left.key.toList === right.key.toList &&
        left.value.map(_.toList) === right.value.map(_.toList) &&
        left.headers.view.mapValues(_.toList).toMap === right.headers.view.mapValues(_.toList).toMap,
      s"${left.show} did not equal ${right.show}",
      s"${left.show} equals ${right.show}"
    )
}

private class JsonScheduleMatcher(right: JsonSchedule) extends Matcher[JsonSchedule] {
  override def apply(left: JsonSchedule): MatchResult =
    MatchResult(
      left.time === right.time &&
        left.topic === right.topic &&
        left.key === right.key &&
        left.value === right.value &&
        left.headers === right.headers,
      s"${left.show} did not equal ${right.show}",
      s"${left.show} equals ${right.show}"
    )
}

private class ProducerRecordMatcher[K : Eq : Show, V : Eq : Show](right: ProducerRecord[K, V])
    extends Matcher[ProducerRecord[K, V]] {
  override def apply(left: ProducerRecord[K, V]): MatchResult =
    MatchResult(
      left.topic === right.topic &&
        left.partition === right.partition &&
        left.timestamp === right.timestamp &&
        left.key === right.key &&
        left.value === right.value &&
        left.headers === right.headers,
      s"${left.show} did not equal ${right.show}",
      s"${left.show} equals ${right.show}"
    )
}

trait ScheduleMatchers {
  def equalSchedule(expectedSchedule: AvroSchedule): AvroScheduleMatcher = AvroScheduleMatcher(expectedSchedule)

  def equalSchedule(expectedSchedule: JsonSchedule): JsonScheduleMatcher = JsonScheduleMatcher(expectedSchedule)
}

trait ProducerRecordMatchers {
  def equalProducerRecord[K : Eq : Show, V : Eq : Show](
      expectedProducerRecord: ProducerRecord[K, V]
  ): ProducerRecordMatcher[K, V] =
    ProducerRecordMatcher(expectedProducerRecord)
}
