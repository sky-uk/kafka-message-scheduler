package com.sky.kms.actors

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.domain._
import monix.execution.{Cancelable, Scheduler => MonixScheduler}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration

class SchedulingActor(publisher: ActorRef, monixScheduler: MonixScheduler) extends Actor with ActorLogging {

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  override def receive: Receive = waitForInit orElse stop

  private val waitForInit: Receive = {
    case Init =>
      log.info("Initialising new scheduling actor.")
      context.become(receiveWithSchedules(Map.empty))
      sender ! Ack
  }

  private def receiveWithSchedules(schedules: Map[ScheduleId, Cancelable]): Receive = {

    val handleSchedulingMessage: PartialFunction[Any, Map[ScheduleId, Cancelable]] = {
      case CreateOrUpdate(scheduleId: ScheduleId, schedule: Schedule) =>
        schedules.get(scheduleId).fold(log.info(s"Creating schedule $scheduleId")) { schedule =>
          log.info(s"Updating schedule $scheduleId")
          schedule.cancel()
        }

        val cancellable = monixScheduler.scheduleOnce(timeFromNow(schedule.time))(publisher ! PublisherActor.Trigger(scheduleId, schedule))
        schedules + (scheduleId -> cancellable)

      case Cancel(scheduleId: String) =>
        schedules.get(scheduleId).fold(log.warning(s"Unable to cancel $scheduleId as it does not exist.")) { schedule =>
          schedule.cancel()
          log.info(s"Cancelled schedule $scheduleId")
        }
        schedules - scheduleId
    }

    (handleSchedulingMessage andThen updateStateAndAck) orElse stop
  }

  private def updateStateAndAck(schedules: Map[ScheduleId, Cancelable]): Unit = {
    context.become(receiveWithSchedules(schedules))
    sender ! Ack
  }

  private val stop: Receive = {
    case UpstreamFailure(t) =>
      log.error(t, "Reader stream has died")
      context stop self
  }

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
    system.actorOf(Props(new SchedulingActor(publisherActor, MonixScheduler(system.dispatcher))))
}
