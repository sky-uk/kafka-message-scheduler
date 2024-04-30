package uk.sky.scheduler.util

import cats.Show
import cats.syntax.all.*
import org.scalatest.matchers.{MatchResult, Matcher}
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule

private given Show[AvroSchedule] = Show.show { schedule =>
  s"AvroSchedule(time=${schedule.time}, topic=${schedule.topic}, key=${schedule.key.toList}, value=${schedule.value
      .map(_.toList)}, headers=${schedule.headers.map(_.view.mapValues(_.toList).toMap)})"
}

private given Show[JsonSchedule] = Show.show { schedule =>
  s"AvroSchedule(time=${schedule.time}, topic=${schedule.topic}, key=${schedule.key}, value=${schedule.value}, headers=${schedule.headers})"
}

private class AvroScheduleMatcher(right: AvroSchedule) extends Matcher[AvroSchedule] {
  private def matchHeaders(left: Option[Map[String, Array[Byte]]], right: Option[Map[String, Array[Byte]]]): Boolean =
    (left, right) match {
      case (None, None)              => true
      case (Some(left), Some(right)) => left.view.mapValues(_.toList).toMap === right.view.mapValues(_.toList).toMap
      case _                         => false
    }

  override def apply(left: AvroSchedule): MatchResult =
    MatchResult(
      left.time === right.time &&
        left.topic === right.topic &&
        left.key.toList === right.key.toList &&
        left.value.map(_.toList) === right.value.map(_.toList) &&
        matchHeaders(left.headers, right.headers),
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

trait ScheduleMatchers {
  def equalSchedule(expectedSchedule: AvroSchedule): AvroScheduleMatcher = AvroScheduleMatcher(expectedSchedule)

  def equalSchedule(expectedSchedule: JsonSchedule): JsonScheduleMatcher = JsonScheduleMatcher(expectedSchedule)
}
