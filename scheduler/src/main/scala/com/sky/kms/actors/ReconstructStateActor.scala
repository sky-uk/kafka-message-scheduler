package com.sky.kms.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import com.sky.kms.actors.PublisherActor.DownstreamFailure
import com.sky.kms.actors.ReconstructStateActor._
import com.sky.kms.domain.{Schedule, ScheduleId}

class ReconstructStateActor extends Actor with ActorLogging {

  override def receive: Receive =
    receiveWithSchedules(Map.empty) orElse stopOnError

  private def receiveWithSchedules(
      schedules: Map[ScheduleId, Schedule]): Receive = {

    case ProcessSchedule(id, schedule) =>
      context become receiveWithSchedules(schedules + (id -> schedule))

    case SchedulesToBeProcessed =>
      val (scheduleMessages, scheduleDeletes) = schedules.partition {
        case (_, schedule) => schedule.value.isDefined
      }

      val yetToBeProcessed = scheduleMessages.filter {
        case (_, event) =>
          !scheduleDeletes.values.map(_.key).toList.contains(event.key)
      }

      sender ! yetToBeProcessed
  }

  private def stopOnError: Receive = {
    case DownstreamFailure(t) =>
      log.error(t, "Reconstructing state stream has died")
      context stop self
  }
}

object ReconstructStateActor {

  sealed trait ReconstructingMessage

  case class ProcessSchedule(id: ScheduleId, schedule: Schedule)
      extends ReconstructingMessage

  case object SchedulesToBeProcessed extends ReconstructingMessage

  def create()(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props(new ReconstructStateActor()))

}
