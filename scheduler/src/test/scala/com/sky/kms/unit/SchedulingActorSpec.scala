package com.sky.kms.unit

import java.util.UUID

import akka.actor.ActorRef
import akka.event.LoggingAdapter
import akka.testkit.{ImplicitSender, TestActorRef, TestProbe}
import com.miguno.akka.testing.VirtualTime
import com.sky.kms.actors.PublisherActor.Trigger
import com.sky.kms.actors.SchedulingActor
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.base.AkkaBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain._
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar

class SchedulingActorSpec extends AkkaBaseSpec with ImplicitSender with MockitoSugar with Eventually {

  "A scheduling actor" must {
    "schedule new messages at the given time" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule

      createSchedule(scheduleId, schedule)

      advanceToTimeFrom(schedule, now)
      probe.expectMsg(Trigger(scheduleId, schedule))
    }

    "cancel schedules when a cancel message is received" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule
      createSchedule(scheduleId, schedule)

      cancelSchedule(scheduleId)

      advanceToTimeFrom(schedule)
      probe.expectNoMsg
    }

    "warn and do nothing when schedule cancelled twice" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule
      createSchedule(scheduleId, schedule)
      cancelSchedule(scheduleId)

      cancelSchedule(scheduleId)
      verify(mockLogger).warning(s"Couldn't cancel $scheduleId")
    }

    "cancel previous schedule when updating an existing schedule" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule
      createSchedule(scheduleId, schedule)

      val updatedSchedule = schedule.copy(time = schedule.time.plusMinutes(5))
      createSchedule(scheduleId, updatedSchedule)

      advanceToTimeFrom(schedule)
      probe.expectNoMsg

      advanceToTimeFrom(updatedSchedule, schedule.timeInMillis)
      probe.expectMsg(Trigger(scheduleId, updatedSchedule))
    }

    "accept scheduling messages only after it has received an Init" in {
      val actorRef = TestActorRef(new SchedulingActor(TestProbe().ref, system.scheduler))
      val (scheduleId, schedule) = generateSchedule

      actorRef ! CreateOrUpdate(scheduleId, schedule)
      expectNoMsg()

      init(actorRef)

      actorRef ! CreateOrUpdate(scheduleId, schedule)
      expectMsg(Ack)
    }

    "stop when receiving an upstream failure" in new SchedulingActorTest {
      watch(schedulingActor)

      schedulingActor ! UpstreamFailure(new Exception("boom!"))

      expectTerminated(schedulingActor)
    }
  }

  private class SchedulingActorTest {

    val mockLogger = mock[LoggingAdapter]
    val time = new VirtualTime
    val probe = TestProbe()

    val schedulingActor = TestActorRef(new SchedulingActor(probe.ref, time.scheduler) {
      override def log: LoggingAdapter = mockLogger
    })

    val now = System.currentTimeMillis()

    init(schedulingActor)

    def advanceToTimeFrom(schedule: Schedule, startTime: Long = now): Unit =
      time.advance(schedule.timeInMillis - startTime)

    def createSchedule(scheduleId: ScheduleId, schedule: Schedule): Unit = {
      schedulingActor ! CreateOrUpdate(scheduleId, schedule)
      expectMsg(Ack)
    }

    def cancelSchedule(scheduleId: ScheduleId): Unit = {
      schedulingActor ! Cancel(scheduleId)
      expectMsg(Ack)
    }
  }

  private def generateSchedule: (ScheduleId, Schedule) =
    (UUID.randomUUID().toString, random[Schedule])

  private def init(actorRef: ActorRef) = {
    actorRef ! Init
    expectMsg(Ack)
  }

}