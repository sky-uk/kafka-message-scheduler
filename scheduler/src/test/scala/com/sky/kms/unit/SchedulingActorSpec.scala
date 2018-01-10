package com.sky.kms.unit

import java.util.UUID

import akka.Done
import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.stream.QueueOfferResult
import akka.stream.scaladsl.SourceQueueWithComplete
import akka.testkit.{ImplicitSender, TestActorRef}
import cats.syntax.show._
import com.miguno.akka.testing.VirtualTime
import com.sky.kms.SchedulingActor
import com.sky.kms.SchedulingActor._
import com.sky.kms.base.AkkaBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain._
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.Future

class SchedulingActorSpec extends AkkaBaseSpec with ImplicitSender with MockitoSugar with Eventually {

  "A scheduling actor" must {
    "schedule new messages at the given time" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule()

      createSchedule(scheduleId, schedule)

      advanceToTimeFrom(schedule, now)
      verify(mockSourceQueue).offer((scheduleId, schedule.toScheduledMessage))
    }

    "cancel schedules when a cancel message is received" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule()
      createSchedule(scheduleId, schedule)

      cancelSchedule(scheduleId)
      verify(mockLogger).info(s"Cancelled schedule $scheduleId")

      advanceToTimeFrom(schedule)
      verify(mockSourceQueue, never()).offer((scheduleId, schedule.toScheduledMessage))
    }

    "warn and do nothing when schedule cancelled twice" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule()
      createSchedule(scheduleId, schedule)
      cancelSchedule(scheduleId)

      cancelSchedule(scheduleId)
      verify(mockLogger).warning(s"Couldn't cancel $scheduleId")
    }

    "cancel previous schedule when updating an existing schedule" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule()
      createSchedule(scheduleId, schedule)

      val updatedSchedule = schedule.copy(time = schedule.time.plusMinutes(5))
      createSchedule(scheduleId, updatedSchedule)

      advanceToTimeFrom(schedule)
      verify(mockSourceQueue, never()).offer((scheduleId, schedule.toScheduledMessage))

      advanceToTimeFrom(updatedSchedule, schedule.timeInMillis)
      verify(mockSourceQueue).offer((scheduleId, updatedSchedule.toScheduledMessage))
    }

    "accept scheduling messages only after it has received an Init" in {
      val mockQueue = {
        val queue = mock[SourceQueueWithComplete[(ScheduleId, ScheduledMessage)]]
        when(queue.watchCompletion()).thenReturn(Future.successful(Done))
        queue
      }

      val actorRef = TestActorRef(new SchedulingActor(mockQueue, system.scheduler))
      val (scheduleId, schedule) = generateSchedule()

      actorRef ! CreateOrUpdate(scheduleId, schedule)
      expectNoMsg()

      init(actorRef)

      actorRef ! CreateOrUpdate(scheduleId, schedule)
      expectMsg(Ack)
    }

    "stop when the queue has been terminated" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule()
      when(mockSourceQueue.offer((scheduleId, schedule.toScheduledMessage)))
        .thenReturn(Future.failed(new Exception("Test")))

      createSchedule(scheduleId, schedule)
      watch(actorRef)

      advanceToTimeFrom(schedule, now)

      expectTerminated(actorRef)
    }

    "fail the queue and stop when receiving a failure message" in new SchedulingActorTest {
      watch(actorRef)
      val exception = new Exception("Test")

      actorRef ! UpstreamFailure(exception)

      verify(mockSourceQueue).fail(exception)
      expectTerminated(actorRef)
    }

    val queueOfferResults = List(
      QueueOfferResult.Dropped,
      QueueOfferResult.QueueClosed,
      QueueOfferResult.Failure(new Exception("Test"))
    )

    queueOfferResults.foreach { queueOfferResult =>
      s"warn and do nothing when queue offer result is $queueOfferResult" in new SchedulingActorTest {
        val (scheduleId, schedule) = generateSchedule()
        when(mockSourceQueue.offer((scheduleId, schedule.toScheduledMessage)))
          .thenReturn(Future.successful(queueOfferResult))

        createSchedule(scheduleId, schedule)

        advanceToTimeFrom(schedule, now)

        eventually {
          verify(mockLogger).warning(ScheduleQueueOfferResult(scheduleId, queueOfferResult).show)
        }
      }
    }
  }

  private class SchedulingActorTest {

    val mockLogger = mock[LoggingAdapter]
    val mockSourceQueue = {
      val queue = mock[SourceQueueWithComplete[(ScheduleId, ScheduledMessage)]]
      when(queue.watchCompletion()).thenReturn(Future.successful(Done))
      queue
    }

    val now = System.currentTimeMillis()

    val time = new VirtualTime

    val actorRef = TestActorRef(new SchedulingActor(mockSourceQueue, time.scheduler) {
      override def log: LoggingAdapter = mockLogger
    })

    init(actorRef)

    def advanceToTimeFrom(schedule: Schedule, startTime: Long = now): Unit =
      time.advance(schedule.timeInMillis - startTime)

    def createSchedule(scheduleId: ScheduleId, schedule: Schedule) {
      actorRef ! CreateOrUpdate(scheduleId, schedule)
      expectMsg(Ack)
    }

    def cancelSchedule(scheduleId: ScheduleId) {
      actorRef ! Cancel(scheduleId)
      expectMsg(Ack)
    }
  }

  private def generateSchedule(): (ScheduleId, Schedule) =
    (UUID.randomUUID().toString, random[Schedule])

  private def init(actorRef: ActorRef) = {
    actorRef ! Init
    expectMsg(Ack)
  }

}