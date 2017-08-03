package com.sky.kafka.message.scheduler

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.stream.scaladsl.SourceQueue
import com.sky.kafka.message.scheduler.SchedulingActor.{Ack, Cancel, CreateOrUpdate, Init}
import com.sky.kafka.message.scheduler.domain.{Schedule, ScheduleId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

object SchedulingActor {

  sealed trait SchedulingMessage

  case object Init

  case object Ack

  case class CreateOrUpdate(scheduleId: ScheduleId, schedule: Schedule) extends SchedulingMessage

  case class Cancel(scheduleId: ScheduleId) extends SchedulingMessage

  def props(queue: SourceQueue[(ScheduleId, Schedule)], scheduler: Scheduler): Props =
    Props(new SchedulingActor(queue, scheduler))

//  def reader(implicit system: ActorSystem): Reader[AppConfig, ActorRef] = {
//    SchedulerStreamRunner.reader.map { runner =>
//      system.actorOf(SchedulingActor.props(runner.runningPublisherStream, system.scheduler))
//    }
//  }

}

class SchedulingActor(sourceQueue: SourceQueue[(String, Schedule)], scheduler: Scheduler) extends Actor with ActorLogging {

  override def receive: Receive = receiveScheduleMessages(Map.empty)

  def receiveScheduleMessages(schedules: Map[ScheduleId, Cancellable]): Receive = {

    val receiveCreateOrUpdateMessage: PartialFunction[Any, Map[ScheduleId, Cancellable]] = {
      case CreateOrUpdate(scheduleId: ScheduleId, schedule: Schedule) =>
        if (cancel(scheduleId, schedules))
          log.info(s"Updating schedule $scheduleId")
        else
          log.info(s"Creating schedule $scheduleId")
        val cancellable = scheduler.scheduleOnce(timeFromNow(schedule.time))(sourceQueue.offer((scheduleId, schedule)))
        schedules + (scheduleId -> cancellable)
    }

    val receiveCancelMessage: PartialFunction[Any, Map[ScheduleId, Cancellable]] = {
      case Cancel(scheduleId: String) =>
        if (cancel(scheduleId, schedules))
          log.info(s"Cancelled schedule $scheduleId")
        else
          log.warning(s"Couldn't cancel $scheduleId")
        schedules - scheduleId
    }

    val receiveInitMessage: PartialFunction[Any, Unit] = {
      case Init => sender ! Ack
    }

    receiveCreateOrUpdateMessage orElse receiveCancelMessage andThen updateStateAndAck orElse receiveInitMessage
  }

  def updateStateAndAck(schedules: Map[ScheduleId, Cancellable]): Unit = {
    context.become(receiveScheduleMessages(schedules))
    sender ! Ack
  }

  def cancel(scheduleId: ScheduleId, schedules: Map[ScheduleId, Cancellable]): Boolean =
    schedules.get(scheduleId).exists(_.cancel())

  def timeFromNow(time: OffsetDateTime): FiniteDuration = {
    val offset = ChronoUnit.MILLIS.between(OffsetDateTime.now, time)
    FiniteDuration(offset, TimeUnit.MILLISECONDS)
  }
}
