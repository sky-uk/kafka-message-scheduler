package com.sky.kms.actors

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.MILLIS

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.domain._
import com.sky.kms.monitoring._
import monix.execution.{Cancelable, Scheduler => MonixScheduler}

import scala.concurrent.duration._

class SchedulingActor(publisher: ActorRef, monixScheduler: MonixScheduler, monitoring: Monitoring) extends Actor with ActorLogging {

  override def receive: Receive = initSchedules(Map.empty)

  private def initSchedules(schedules: Map[ScheduleId, ScheduleEvent]): Receive = {

    val handleSchedulingMessage: PartialFunction[Any, Map[ScheduleId, ScheduleEvent]] = {
      case CreateOrUpdate(scheduleId: ScheduleId, schedule: ScheduleEvent) =>
        schedules + (scheduleId -> schedule)

      case Cancel(scheduleId: String) =>
        schedules - scheduleId
    }

    val finishInitialisation: Receive = {
      case Initialised =>
        log.info("State initialised - scheduling stored schedules")
        val scheduled = schedules.map { case (scheduleId, schedule) => monitoring.scheduleReceived(); scheduleId -> scheduleOnce(scheduleId, schedule) }
        updateStateAndAck(receiveWithSchedules(_), scheduled)
    }

    val updateSchedulesThenAck = handleSchedulingMessage andThen (updateStateAndAck[ScheduleEvent](initSchedules, _))

    updateSchedulesThenAck orElse finishInitialisation
  }

  private def receiveWithSchedules(schedules: Map[ScheduleId, Cancelable]): Receive = {

    val handleSchedulingMessage: PartialFunction[Any, Map[ScheduleId, Cancelable]] = {
      case CreateOrUpdate(scheduleId: ScheduleId, schedule: ScheduleEvent) =>
        schedules.get(scheduleId).foreach(_.cancel())
        val cancellable = scheduleOnce(scheduleId, schedule)
        log.info(s"Scheduled $scheduleId")

        monitoring.scheduleReceived()
        schedules + (scheduleId -> cancellable)

      case Cancel(scheduleId: String) =>
        schedules.get(scheduleId).foreach { schedule =>
          schedule.cancel()
          monitoring.scheduleDone()
          log.info(s"Cancelled schedule $scheduleId")
        }
        schedules - scheduleId
    }

    val updateCancelables = handleSchedulingMessage andThen (updateStateAndAck[Cancelable](receiveWithSchedules, _))

    updateCancelables orElse stop
  }

  private def scheduleOnce(scheduleId: ScheduleId, schedule: ScheduleEvent): Cancelable =
    monixScheduler.scheduleOnce(MILLIS.between(OffsetDateTime.now, schedule.time).millis) {
      publisher ! PublisherActor.Trigger(scheduleId, schedule)
    }

  private def updateStateAndAck[T](f: Map[ScheduleId, T] => Receive, state: Map[ScheduleId, T]): Unit = {
    context.become(f(state))
    sender ! Ack
  }

  private val stop: Receive = {
    case UpstreamFailure(t) =>
      log.error(t, "Reader stream has died")
      context stop self

  }
}

object SchedulingActor {

  sealed trait SchedulingMessage

  case class CreateOrUpdate(scheduleId: ScheduleId, schedule: ScheduleEvent) extends SchedulingMessage

  case class Cancel(scheduleId: ScheduleId) extends SchedulingMessage

  case object Initialised

  case object Ack

  case class UpstreamFailure(t: Throwable)

  def create(publisherActor: ActorRef)(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new SchedulingActor(publisherActor, MonixScheduler(system.dispatcher), new KamonMonitoring())), "scheduling-actor")
}
