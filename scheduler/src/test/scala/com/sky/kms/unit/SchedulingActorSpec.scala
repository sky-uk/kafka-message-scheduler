package com.sky.kms.unit

import java.util.UUID

import akka.actor.ActorRef
import akka.testkit.{ImplicitSender, TestActorRef, TestProbe}
import com.sky.kms.actors.PublisherActor.Trigger
import com.sky.kms.actors.SchedulingActor
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.base.{AkkaBaseSpec, SimpleCounterMonitoring}
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain._
import monix.execution.schedulers.TestScheduler
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._

class SchedulingActorSpec extends AkkaBaseSpec with ImplicitSender with MockitoSugar with Eventually {

  val NoMsgTimeout = 2 seconds

  "A scheduling actor" must {
    "schedule new messages at the given time" in new TestContext {
      val (scheduleId, schedule) = generateSchedule

      createSchedule(scheduleId, schedule)

      advanceToTimeFrom(schedule, now)
      probe.expectMsg(Trigger(scheduleId, schedule))
    }

    "cancel schedules when a cancel message is received" in new TestContext {
      val (scheduleId, schedule) = generateSchedule
      createSchedule(scheduleId, schedule)

      cancelSchedule(scheduleId)

      advanceToTimeFrom(schedule)
      probe.expectNoMessage(NoMsgTimeout)
    }

    "cancel previous schedule when updating an existing schedule" in new TestContext {
      val (scheduleId, schedule) = generateSchedule
      createSchedule(scheduleId, schedule)

      val updatedSchedule = schedule.copy(time = schedule.time.plusMinutes(5))
      createSchedule(scheduleId, updatedSchedule)

      advanceToTimeFrom(schedule)
      probe.expectNoMessage(NoMsgTimeout)

      advanceToTimeFrom(updatedSchedule, schedule.timeInMillis)
      probe.expectMsg(Trigger(scheduleId, updatedSchedule))
    }

    "accept scheduling messages only after it has received an Init" in {
      val actorRef = TestActorRef(new SchedulingActor(TestProbe().ref, TestScheduler(), new SimpleCounterMonitoring()))
      val (scheduleId, schedule) = generateSchedule

      actorRef ! CreateOrUpdate(scheduleId, schedule)
      expectNoMessage(NoMsgTimeout)

      init(actorRef)

      actorRef ! CreateOrUpdate(scheduleId, schedule)
      expectMsg(Ack)
    }

    "stop when receiving an upstream failure" in new TestContext {
      watch(schedulingActor)

      schedulingActor ! UpstreamFailure(new Exception("boom!"))

      expectTerminated(schedulingActor)
    }

    "update monitoring when new schedule is received" in new TestContext {
      val (scheduleId, schedule) = generateSchedule

      createSchedule(scheduleId, schedule)

      advanceToTimeFrom(schedule, now)
      eventually {
        scheduleReceivedCounter shouldBe 1L
      }
    }

    "update monitoring when a cancel message is received" in new TestContext {
      val (scheduleId, schedule) = generateSchedule
      createSchedule(scheduleId, schedule)

      cancelSchedule(scheduleId)

      advanceToTimeFrom(schedule)
      eventually {
        scheduleDoneCounter shouldBe 1L
      }
    }
  }

  private class TestContext {

    val monitoring = new SimpleCounterMonitoring()
    val testScheduler = TestScheduler()
    val probe = TestProbe()
    val schedulingActor = TestActorRef(new SchedulingActor(probe.ref, testScheduler, monitoring))
    val now = System.currentTimeMillis()

    init(schedulingActor)

    def advanceToTimeFrom(schedule: Schedule, startTime: Long = now): Unit =
      testScheduler.tick((schedule.timeInMillis - startTime).millis)

    def createSchedule(scheduleId: ScheduleId, schedule: Schedule): Unit = {
      schedulingActor ! CreateOrUpdate(scheduleId, schedule)
      expectMsg(Ack)
    }

    def cancelSchedule(scheduleId: ScheduleId): Unit = {
      schedulingActor ! Cancel(scheduleId)
      expectMsg(Ack)
    }

    def scheduleReceivedCounter: Long = monitoring.scheduleReceivedCounter.get()

    def scheduleDoneCounter: Long = monitoring.scheduleDoneCounter.get()
  }

  private def generateSchedule: (ScheduleId, Schedule) =
    (UUID.randomUUID().toString, random[Schedule])

  private def init(actorRef: ActorRef) = {
    actorRef ! Init
    expectMsg(Ack)
  }

}