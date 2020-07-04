package com.sky.kms.utils

import com.sky.kms.domain.ScheduleEvent
import org.scalatest.Matchers
import org.scalatest.matchers.{MatchResult, Matcher}

trait ScheduleMatcher {

  class ScheduleEventMatcher(expected: ScheduleEvent) extends Matcher[ScheduleEvent] with Matchers {
    def apply(left: ScheduleEvent): MatchResult = {
      val matches = (left.inputTopic === expected.inputTopic) &&
        (left.outputTopic === expected.outputTopic) &&
        (left.key === expected.key) &&
        (left.headers.mapValues(_.toList) === expected.headers.mapValues(_.toList)) &&
        PartialFunction.cond((left.value, expected.value)) {
          case (None, None)       => true
          case (Some(a), Some(b)) => a === b
        }

      MatchResult(matches,
                  s"Schedule: $left did not match expected schedule $expected",
                  s"Schedule: $left matched schedule $expected")
    }
  }

  def matchScheduleEvent(expected: ScheduleEvent) = new ScheduleEventMatcher(expected)

}
