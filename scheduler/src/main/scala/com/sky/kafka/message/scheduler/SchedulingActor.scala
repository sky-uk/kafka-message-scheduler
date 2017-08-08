package com.sky.kafka.message.scheduler

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.stream.{ActorMaterializer, QueueOfferResult}
import akka.stream.scaladsl.SourceQueue
import cats.data.Reader
import com.sky.kafka.message.scheduler.SchedulingActor._
import com.sky.kafka.message.scheduler.config.AppConfig
import com.sky.kafka.message.scheduler.domain.PublishableMessage.ScheduledMessage
import com.sky.kafka.message.scheduler.domain._
import com.sky.kafka.message.scheduler.streams.ScheduledMessagePublisher

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import akka.pattern.pipe

class SchedulingActor(sourceQueue: SourceQueue[(String, ScheduledMessage)], scheduler: Scheduler) extends Actor with ActorLogging {

  override def receive: Receive = receiveScheduleMessages(Map.empty)

  def receiveScheduleMessages(schedules: Map[ScheduleId, Cancellable]): Receive = {

    val receiveSchedulingMessage: PartialFunction[Any, Map[ScheduleId, Cancellable]] = {
      case CreateOrUpdate(scheduleId: ScheduleId, schedule: Schedule) =>
        if (cancel(scheduleId, schedules))
          log.info(s"Updating schedule $scheduleId")
        else
          log.info(s"Creating schedule $scheduleId")
        val cancellable = scheduler.scheduleOnce(timeFromNow(schedule.time))(self ! TriggerMessage(scheduleId, schedule))
        schedules + (scheduleId -> cancellable)

      case Cancel(scheduleId: String) =>
        if (cancel(scheduleId, schedules))
          log.info(s"Cancelled schedule $scheduleId")
        else
          log.warning(s"Couldn't cancel $scheduleId")
        schedules - scheduleId
    }

    val receiveTriggerMessage: PartialFunction[Any, Unit] = {
      case TriggerMessage(scheduleId, schedule) =>
        log.info(s"$scheduleId is due. Adding schedule to queue. Scheduled time was ${schedule.time}")
        sourceQueue.offer((scheduleId, messageFrom(schedule)))
    }

    (receiveSchedulingMessage andThen updateStateAndAck) orElse receiveTriggerMessage orElse {
      case Init => sender ! Ack
    }
  }

  private def updateStateAndAck(schedules: Map[ScheduleId, Cancellable]): Unit = {
    context.become(receiveScheduleMessages(schedules))
    sender ! Ack
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

  case class TriggerMessage(scheduleId: ScheduleId, schedule: Schedule)

  case object Init

  case object Ack

  def reader(implicit system: ActorSystem, mat: ActorMaterializer): Reader[AppConfig, ActorRef] =
    ScheduledMessagePublisher.reader.map(publisher =>
      system.actorOf(props(publisher.stream, system.scheduler), "scheduling-actor")
    )

  private def props(queue: SourceQueue[(ScheduleId, ScheduledMessage)], scheduler: Scheduler): Props =
    Props(new SchedulingActor(queue, scheduler))

}
