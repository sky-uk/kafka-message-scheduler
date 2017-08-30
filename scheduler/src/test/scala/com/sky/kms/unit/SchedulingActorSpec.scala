package com.sky.kms.unit

import java.util.UUID

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.stream.scaladsl.SourceQueue
import akka.testkit.{ImplicitSender, TestActorRef}
import com.miguno.akka.testing.VirtualTime
import com.sky.kms.SchedulingActor
import com.sky.kms.SchedulingActor._
import com.sky.kms.base.AkkaBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

class SchedulingActorSpec extends AkkaBaseSpec with ImplicitSender with MockitoSugar {

  val mockLogger = mock[LoggingAdapter]
  val mockSourceQueue = mock[SourceQueue[(ScheduleId, ScheduledMessage)]]

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
      verifyZeroInteractions(mockSourceQueue)
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

      val actorRef = TestActorRef(new SchedulingActor(mockSourceQueue, system.scheduler))
      val (scheduleId, schedule) = generateSchedule()

      actorRef ! CreateOrUpdate(scheduleId, schedule)
      expectNoMsg()

      init(actorRef)

      actorRef ! CreateOrUpdate(scheduleId, schedule)
      expectMsg(Ack)
    }

  }

  private class SchedulingActorTest {
    val now = System.currentTimeMillis()

    val time = new VirtualTime

    val actorRef = TestActorRef(new SchedulingActor(mockSourceQueue, time.scheduler) {
      override def log: LoggingAdapter = mockLogger
    })

    init(actorRef)

    def advanceToTimeFrom(schedule: Schedule, startTime: Long = now): Unit =
      time.advance(schedule.timeInMillis - startTime)

    def createSchedule(scheduleId: ScheduleId, schedule: Schedule) = {
      actorRef ! CreateOrUpdate(scheduleId, schedule)
      expectMsg(Ack)
    }

    def cancelSchedule(scheduleId: ScheduleId) = {
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