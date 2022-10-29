package com.sky.kms.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.domain._
import com.sky.kms.monitoring._
import monix.execution.{Cancelable, Scheduler => MonixScheduler}

import scala.collection.mutable

class SchedulingActor(publisher: ActorRef, monixScheduler: MonixScheduler, scheduleGauge: ScheduleGauge)
    extends Actor
    with ActorLogging {

  override def receive: Receive = initSchedules

  private def initSchedules: Receive = {

    val schedules = mutable.AnyRefMap.empty[ScheduleId, ScheduleEvent]

    val handleSchedulingMessage: Receive = {
      case CreateOrUpdate(scheduleId: ScheduleId, schedule: ScheduleEvent) =>
        schedules += (scheduleId -> schedule)

      case Cancel(scheduleId: String) =>
        schedules -= scheduleId
    }

    val finishInitialisation: Receive = { case Initialised =>
      log.debug("State initialised - scheduling stored schedules")
      val scheduled = schedules.map { case (scheduleId, schedule) =>
        scheduleGauge.onUpdate()
        scheduleId -> scheduleOnce(scheduleId, schedule)
      }
      log.info("Reloaded state has been scheduled")
      context become receiveWithSchedules(scheduled)
    }

    streamStartedOrFailed orElse {
      (handleSchedulingMessage orElse finishInitialisation) andThen (_ => sender() ! Ack)
    }
  }

  private def receiveWithSchedules(scheduled: mutable.AnyRefMap[ScheduleId, Cancelable]): Receive = {

    val handleSchedulingMessage: Receive = {
      case CreateOrUpdate(scheduleId: ScheduleId, schedule: ScheduleEvent) =>
        scheduled.get(scheduleId).foreach { schedule =>
          schedule.cancel()
          scheduleGauge.onDelete()
          log.info(s"Cancelled and updated $scheduleId")
        }

        val cancellable = scheduleOnce(scheduleId, schedule)
        log.info(
          s"Scheduled $scheduleId from ${schedule.inputTopic} to ${schedule.outputTopic} in ${schedule.delay.toMillis} millis"
        )

        scheduled += (scheduleId -> cancellable)
        scheduleGauge.onUpdate()

      case Cancel(scheduleId: String) =>
        scheduled.get(scheduleId).foreach { schedule =>
          schedule.cancel()
          scheduleGauge.onDelete()
          log.info(s"Cancelled $scheduleId")
        }
        scheduled -= scheduleId
    }

    streamStartedOrFailed orElse {
      handleSchedulingMessage andThen (_ => sender() ! Ack)
    }
  }

  private def scheduleOnce(scheduleId: ScheduleId, schedule: ScheduleEvent): Cancelable =
    monixScheduler.scheduleOnce(schedule.delay) {
      publisher ! PublisherActor.Trigger(scheduleId, schedule)
    }

  private val streamStartedOrFailed: Receive = {
    case UpstreamFailure(t) =>
      log.error(t, "Reader stream has died")
      context stop self
    case StreamStarted      =>
      sender() ! Ack
  }
}

object SchedulingActor {

  sealed trait SchedulingMessage

  case class CreateOrUpdate(scheduleId: ScheduleId, schedule: ScheduleEvent) extends SchedulingMessage

  case class Cancel(scheduleId: ScheduleId) extends SchedulingMessage

  case object StreamStarted

  case object Initialised

  case object Ack

  case class UpstreamFailure(t: Throwable)

  def create(publisherActor: ActorRef, scheduleGauge: ScheduleGauge)(implicit system: ActorSystem): ActorRef =
    system.actorOf(
      Props(new SchedulingActor(publisherActor, MonixScheduler(system.dispatcher), scheduleGauge)),
      "scheduling-actor"
    )
}
