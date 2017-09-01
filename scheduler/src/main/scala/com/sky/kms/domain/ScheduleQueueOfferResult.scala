package com.sky.kms.domain

import akka.stream.QueueOfferResult
import akka.stream.QueueOfferResult.{Dropped, Enqueued, QueueClosed}
import cats.Show
import cats.Show.show

case class ScheduleQueueOfferResult(scheduleId: ScheduleId, queueOfferResult: QueueOfferResult)

object ScheduleQueueOfferResult {

  implicit val queueOfferResultShow: Show[ScheduleQueueOfferResult] = show {
    case ScheduleQueueOfferResult(scheduleId, Enqueued) =>
      s"$scheduleId enqueued successfully"
    case ScheduleQueueOfferResult(scheduleId, Dropped) =>
      s"$scheduleId was dropped from queue, check the chosen overflow strategy"
    case ScheduleQueueOfferResult(scheduleId, QueueOfferResult.Failure(t)) =>
      s"An error occurred when attempting to enqueue the $scheduleId: ${t.getMessage}"
    case ScheduleQueueOfferResult(scheduleId, QueueClosed) =>
      s"Unable to enqueue $scheduleId because the downstream source queue has been completed/closed"
  }

}
