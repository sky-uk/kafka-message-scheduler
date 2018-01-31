package com.sky.kms.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.pattern.pipe
import akka.stream.QueueOfferResult
import akka.stream.scaladsl.SourceQueueWithComplete
import cats.syntax.show._
import com.sky.kms.actors.PublisherActor.{ScheduleQueue, Trigger}
import com.sky.kms.actors.SchedulingActor.DownstreamFailure
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain.{Schedule, ScheduleId, ScheduleQueueOfferResult}

import scala.util.{Failure, Success}

class PublisherActor(queue: ScheduleQueue) extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  override def receive: Receive = {
    case Trigger(scheduleId, schedule) =>
      log.info(s"$scheduleId is due. Adding schedule to queue. Scheduled time was ${schedule.time}")
      queue.offer((scheduleId, messageFrom(schedule))) onComplete {
        case Success(QueueOfferResult.Enqueued) =>
          log.info(ScheduleQueueOfferResult(scheduleId, QueueOfferResult.Enqueued).show)
        case Success(res) =>
          log.warning(ScheduleQueueOfferResult(scheduleId, res).show)
        case Failure(_: IllegalStateException) =>
          log.warning(s"Failed to enqueue $scheduleId because the queue buffer is full. Retrying until enqueued.")
          self ! Trigger(scheduleId, schedule)
        case Failure(t) =>
          log.error(t, s"Failed to enqueue $scheduleId because the queue has terminated. Shutting down.")
          self ! DownstreamFailure(t)
      }

    case DownstreamFailure(t) =>
      log.error(t, "Publisher stream has died")
      context stop self
  }

  private def messageFrom(schedule: Schedule) =
    ScheduledMessage(schedule.topic, schedule.key, schedule.value)
}

object PublisherActor {

  type ScheduleQueue = SourceQueueWithComplete[(ScheduleId, ScheduledMessage)]

  case class Trigger(scheduleId: ScheduleId, schedule: Schedule)

  def create(queue: ScheduleQueue)(implicit system: ActorSystem): ActorRef = {
    implicit val ec = system.dispatcher
    val queueCompletionFuture = queue.watchCompletion().recover { case t => DownstreamFailure(t) }
    val ref = system.actorOf(Props(new PublisherActor(queue)))
    queueCompletionFuture pipeTo ref
    ref
  }
}
