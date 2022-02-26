package com.sky.kms.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.QueueOfferResult
import akka.stream.scaladsl.SourceQueueWithComplete
import cats.syntax.show._
import com.sky.kms.Start
import com.sky.kms.actors.PublisherActor.{DownstreamFailure, Init, ScheduleQueue, Trigger}
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain.{ScheduleEvent, ScheduleId, ScheduleQueueOfferResult}

import scala.util.{Failure, Success}

class PublisherActor extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  override def receive: Receive = waitForQueue

  private def waitForQueue: Receive = { case Init(queue) =>
    queue.watchCompletion().failed.foreach(t => self ! DownstreamFailure(t))
    context become (receiveWithQueue(queue) orElse stopOnError)
  }

  private def receiveWithQueue(queue: ScheduleQueue): Receive = { case Trigger(scheduleId, schedule) =>
    queue.offer((scheduleId, messageFrom(schedule))) onComplete {
      case Success(QueueOfferResult.Enqueued) =>
        log.debug(ScheduleQueueOfferResult(scheduleId, QueueOfferResult.Enqueued).show)
      case Success(res)                       =>
        log.warning(ScheduleQueueOfferResult(scheduleId, res).show)
      case Failure(t)                         =>
        log.error(t, s"Failed to enqueue $scheduleId")
        self ! DownstreamFailure(t)
    }
  }

  private def stopOnError: Receive = { case DownstreamFailure(t) =>
    log.error(t, "Publisher stream has died")
    context stop self
  }

  private def messageFrom(schedule: ScheduleEvent) =
    ScheduledMessage(schedule.inputTopic, schedule.outputTopic, schedule.key, schedule.value, schedule.headers)
}

object PublisherActor {

  type ScheduleQueue = SourceQueueWithComplete[(ScheduleId, ScheduledMessage)]

  case class Init(queue: ScheduleQueue)

  case class Trigger(scheduleId: ScheduleId, schedule: ScheduleEvent)

  case class DownstreamFailure(t: Throwable)

  def create(implicit system: ActorSystem): ActorRef =
    system.actorOf(Props[PublisherActor], "publisher-actor")

  def init(queue: ScheduleQueue): Start[Unit] =
    Start(_.publisherActor ! Init(queue))
}
