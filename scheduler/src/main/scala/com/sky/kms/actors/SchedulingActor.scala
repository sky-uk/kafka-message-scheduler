package com.sky.kms.actors

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor._
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.domain._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration

class SchedulingActor(publisher: ActorRef, akkaScheduler: Scheduler) extends Actor with ActorLogging {

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  override def receive: Receive = waitForInit orElse stop

  private val waitForInit: Receive = {
    case Init =>
      log.info("Initialising new scheduling actor.")
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
        val cancellable = akkaScheduler.scheduleOnce(timeFromNow(schedule.time))(publisher ! PublisherActor.Trigger(scheduleId, schedule))
        schedules + (scheduleId -> cancellable)

      case Cancel(scheduleId: String) =>
        if (cancel(scheduleId, schedules))
          log.info(s"Cancelled schedule $scheduleId")
        else
          log.warning(s"Couldn't cancel $scheduleId")
        schedules - scheduleId
    }

    (handleSchedulingMessage andThen updateStateAndAck) orElse stop
  }

  private def updateStateAndAck(schedules: Map[ScheduleId, Cancellable]): Unit = {
    context.become(receiveWithSchedules(schedules))
    sender ! Ack
  }

  private val stop: Receive = {
    case UpstreamFailure(t) =>
      log.error(t, "Reader stream has died")
      context stop self
  }

  private def cancel(scheduleId: ScheduleId, schedules: Map[ScheduleId, Cancellable]): Boolean =
    schedules.get(scheduleId).exists(_.cancel())

  private def timeFromNow(time: OffsetDateTime): FiniteDuration = {
    val offset = ChronoUnit.MILLIS.between(OffsetDateTime.now, time)
    FiniteDuration(offset, TimeUnit.MILLISECONDS)
  }
}

object SchedulingActor {

  sealed trait SchedulingMessage

  case class CreateOrUpdate(scheduleId: ScheduleId, schedule: Schedule) extends SchedulingMessage

  case class Cancel(scheduleId: ScheduleId) extends SchedulingMessage

  case object Init

  case object Ack

  case class UpstreamFailure(t: Throwable)

  def create(publisherActor: ActorRef)(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new SchedulingActor(publisherActor, system.scheduler)))
}
