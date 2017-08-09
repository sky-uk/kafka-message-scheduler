package com.sky.kms

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.SourceQueue
import cats.data.Reader
import com.sky.kms.SchedulingActor._
import com.sky.kms.config.AppConfig
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain._
import com.sky.kms.streams.ScheduledMessagePublisher

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

class SchedulingActor(queue: SourceQueue[(String, ScheduledMessage)], akkaScheduler: Scheduler) extends Actor with ActorLogging {

  override def receive: Receive = waitForInit

  private val waitForInit: Receive = {
    case Init =>
      context.become(receiveWithSchedules(Map.empty))
      sender ! Ack
  }

  private def receiveWithSchedules(schedules: Map[ScheduleId, Cancellable]): Receive = {

    val handleSchedulingMessage: PartialFunction[Any, Map[ScheduleId, Cancellable]] = {
      case CreateOrUpdate(scheduleId: ScheduleId, schedule: Schedule) =>
        if (cancel(scheduleId, schedules))
          log.info(s"Updating schedule $scheduleId")
        else
          log.info(s"Creating schedule $scheduleId")
        val cancellable = akkaScheduler.scheduleOnce(timeFromNow(schedule.time))(self ! Trigger(scheduleId, schedule))
        schedules + (scheduleId -> cancellable)

      case Cancel(scheduleId: String) =>
        if (cancel(scheduleId, schedules))
          log.info(s"Cancelled schedule $scheduleId")
        else
          log.warning(s"Couldn't cancel $scheduleId")
        schedules - scheduleId
    }

    (handleSchedulingMessage andThen updateStateAndAck) orElse handleTrigger
  }

  private def updateStateAndAck(schedules: Map[ScheduleId, Cancellable]): Unit = {
    context.become(receiveWithSchedules(schedules))
    sender ! Ack
  }

  private val handleTrigger: Receive = {
    case Trigger(scheduleId, schedule) =>
      log.info(s"$scheduleId is due. Adding schedule to queue. Scheduled time was ${schedule.time}")
      queue.offer((scheduleId, messageFrom(schedule)))
  }

  private def cancel(scheduleId: ScheduleId, schedules: Map[ScheduleId, Cancellable]): Boolean =
    schedules.get(scheduleId).exists(_.cancel())

  private def timeFromNow(time: OffsetDateTime): FiniteDuration = {
    val offset = ChronoUnit.MILLIS.between(OffsetDateTime.now, time)
    FiniteDuration(offset, TimeUnit.MILLISECONDS)
  }

  private def messageFrom(schedule: Schedule) =
    ScheduledMessage(schedule.topic, schedule.key, schedule.value)
}

object SchedulingActor {

  sealed trait SchedulingMessage

  case class CreateOrUpdate(scheduleId: ScheduleId, schedule: Schedule) extends SchedulingMessage

  case class Cancel(scheduleId: ScheduleId) extends SchedulingMessage

  private case class Trigger(scheduleId: ScheduleId, schedule: Schedule)

  case object Init

  case object Ack

  def reader(implicit system: ActorSystem, mat: ActorMaterializer): Reader[AppConfig, ActorRef] =
    ScheduledMessagePublisher.reader.map(publisher =>
      system.actorOf(props(publisher.stream, system.scheduler), "scheduling-actor")
    )

  private def props(queue: SourceQueue[(ScheduleId, ScheduledMessage)], scheduler: Scheduler): Props =
    Props(new SchedulingActor(queue, scheduler))

}
