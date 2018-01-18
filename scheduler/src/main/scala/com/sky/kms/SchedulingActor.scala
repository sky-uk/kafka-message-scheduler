package com.sky.kms

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor._
import akka.stream.QueueOfferResult
import akka.stream.scaladsl.SourceQueueWithComplete
import cats.syntax.show._
import com.sky.kms.SchedulingActor._
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class SchedulingActor(queue: SourceQueueWithComplete[(String, ScheduledMessage)], akkaScheduler: Scheduler) extends Actor with ActorLogging {

  implicit val ec: ExecutionContextExecutor = context.dispatcher

  override def receive: Receive = waitForInit orElse handleFailureAndStop

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
        val cancellable = akkaScheduler.scheduleOnce(timeFromNow(schedule.time))(self ! Trigger(scheduleId, schedule))
        schedules + (scheduleId -> cancellable)

      case Cancel(scheduleId: String) =>
        if (cancel(scheduleId, schedules))
          log.info(s"Cancelled schedule $scheduleId")
        else
          log.warning(s"Couldn't cancel $scheduleId")
        schedules - scheduleId
    }

    (handleSchedulingMessage andThen updateStateAndAck) orElse handleTrigger orElse handleFailureAndStop
  }

  private def updateStateAndAck(schedules: Map[ScheduleId, Cancellable]): Unit = {
    context.become(receiveWithSchedules(schedules))
    sender ! Ack
  }

  private val handleTrigger: Receive = {
    case Trigger(scheduleId, schedule) =>
      log.info(s"$scheduleId is due. Adding schedule to queue. Scheduled time was ${schedule.time}")
      queue.offer((scheduleId, messageFrom(schedule))) onComplete {
        case Success(QueueOfferResult.Enqueued) =>
          log.info(ScheduleQueueOfferResult(scheduleId, QueueOfferResult.Enqueued).show)
        case Success(res) =>
          log.warning(ScheduleQueueOfferResult(scheduleId, res).show)
        case Failure(t) =>
          log.error(s"Failed to enqueue $scheduleId because the queue has terminated")
          self ! DownstreamFailure(t)
      }
  }

  private val handleFailureAndStop: Receive = {
    val handleFailures: Receive = {
      case UpstreamFailure(t) =>
        log.error(t, "Reader stream has died")
        queue fail t
      case DownstreamFailure(t) =>
        log.error(t, "Publisher stream has died")
    }
    handleFailures andThen (_ => context stop self)
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

  import akka.pattern.pipe

  sealed trait SchedulingMessage

  case class CreateOrUpdate(scheduleId: ScheduleId, schedule: Schedule) extends SchedulingMessage

  case class Cancel(scheduleId: ScheduleId) extends SchedulingMessage

  private case class Trigger(scheduleId: ScheduleId, schedule: Schedule)

  case object Init

  case object Ack

  case class UpstreamFailure(t: Throwable)

  case class DownstreamFailure(t: Throwable)

  def create(queue: SourceQueueWithComplete[(String, ScheduledMessage)])(implicit system: ActorSystem): ActorRef = {
    implicit val ec = system.dispatcher
    val queueCompletionFuture = queue.watchCompletion().recover { case t => DownstreamFailure(t) }
    val ref = system.actorOf(Props(new SchedulingActor(queue, system.scheduler)))
    queueCompletionFuture pipeTo ref
    ref
  }
}
