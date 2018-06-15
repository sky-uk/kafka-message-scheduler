package com.sky.kms.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, Scheduler}
import akka.stream.QueueOfferResult
import akka.stream.scaladsl.SourceQueueWithComplete
import cats.syntax.option._
import cats.syntax.show._
import com.sky.kms.Start
import com.sky.kms.actors.PublisherActor.{DownstreamFailure, Init, ScheduleQueue, Trigger}
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain.{Schedule, ScheduleId, ScheduleQueueOfferResult}

import scala.util.{Failure, Success}

class PublisherActor extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  override def receive: Receive = waitForQueue

  private def waitForQueue: Receive = {
    case Init(queue) =>
      queue.watchCompletion().failed.foreach(t => self ! DownstreamFailure(t))
      context become (receiveWithQueue(queue) orElse stopOnError)
  }

  private def receiveWithQueue(queue: ScheduleQueue): Receive = {
    case Trigger(scheduleId, schedule) =>
      log.info(s"$scheduleId is due. Adding schedule to queue. Scheduled time was ${schedule.time}")
      queue.offer((scheduleId, messageFrom(schedule))) onComplete {
        case Success(QueueOfferResult.Enqueued) =>
          log.info(ScheduleQueueOfferResult(scheduleId, QueueOfferResult.Enqueued).show)
        case Success(res) =>
          log.warning(ScheduleQueueOfferResult(scheduleId, res).show)
        case Failure(t) =>
          log.error(t, s"Failed to enqueue $scheduleId")
          self ! DownstreamFailure(t)
      }
  }

  private def stopOnError: Receive = {
    case DownstreamFailure(t) =>
      log.error(t, "Publisher stream has died")
      context stop self
  }

  private def messageFrom(schedule: Schedule) =
    ScheduledMessage(schedule.topic, schedule.key, schedule.value)
}

object PublisherActor {

  type ScheduleQueue = SourceQueueWithComplete[(ScheduleId, ScheduledMessage)]

  case class Init(queue: ScheduleQueue)

  case class Trigger(scheduleId: ScheduleId, schedule: Schedule)

  case class DownstreamFailure(t: Throwable)

  def create(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props[PublisherActor])

  def init(queue: ScheduleQueue): Start[Unit] =
    Start(_.publisherActor ! Init(queue))
}
