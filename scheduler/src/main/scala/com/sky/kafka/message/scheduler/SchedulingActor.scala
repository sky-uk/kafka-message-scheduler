package com.sky.kafka.message.scheduler

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, Cancellable, Scheduler}
import akka.stream.scaladsl.SourceQueue
import com.sky.kafka.message.scheduler.SchedulingActor.{Ack, CancelSchedule, CreateOrUpdateSchedule}
import com.sky.kafka.message.scheduler.domain.Schedule

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

object SchedulingActor {

  case object Ack

  case class CreateOrUpdateSchedule(scheduleId: String, schedule: Schedule)

  case class CancelSchedule(scheduleId: String)

}

class SchedulingActor(sourceQueue: SourceQueue[(String, Schedule)], scheduler: Scheduler) extends Actor with ActorLogging {

  override def receive: Receive = manageSchedules(Map.empty)

  def manageSchedules(cancellableSchedules: Map[String, Cancellable]): Receive = {
    case CreateOrUpdateSchedule(scheduleId: String, schedule: Schedule) =>
      val timeFromNow = ChronoUnit.MILLIS.between(OffsetDateTime.now, schedule.time)
      val cancellable = scheduler.scheduleOnce(FiniteDuration(timeFromNow, TimeUnit.MILLISECONDS))(sourceQueue.offer((scheduleId, schedule)))
      sender ! Ack
      context.become(manageSchedules(cancellableSchedules + (scheduleId -> cancellable)))
    case CancelSchedule(scheduleId: String) =>
      val cancelled = cancellableSchedules.get(scheduleId).exists(_.cancel())
      if (cancelled)
        log.info(s"Cancelled schedule $scheduleId")
      else
        log.warning(s"Couldn't cancel $scheduleId")
      sender ! Ack
      context.become(manageSchedules(cancellableSchedules - scheduleId))
  }
}
