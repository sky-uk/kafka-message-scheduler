package com.sky.kms.unit

import java.util.UUID

import akka.actor.ActorRef
import akka.testkit.{ImplicitSender, TestActorRef, TestProbe}
import com.sky.kms.actors.PublisherActor.Trigger
import com.sky.kms.actors.SchedulingActor
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.base.AkkaSpecBase
import com.sky.kms.utils.TestDataUtils._
import com.sky.kms.domain._
import com.sky.kms.utils.SimpleCounterMonitoring
import monix.execution.schedulers.TestScheduler
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration._

class SchedulingActorSpec extends AkkaSpecBase with ImplicitSender with MockitoSugar with Eventually {

  val NoMsgTimeout = 2 seconds

  "A scheduling actor" must {

    "schedule its current state after receiving Initialised" in new TestContext {
      val (scheduleId, schedule) = generateSchedule

      createSchedule(scheduleId, schedule)

      testScheduler.state.tasks.isEmpty shouldBe true

      init(schedulingActor)

      testScheduler.state.tasks.nonEmpty shouldBe true
    }

    "not schedules events that were cancelled before receiving Initialised" in new TestContext {
      val (scheduleId, schedule) = generateSchedule

      createSchedule(scheduleId, schedule)

      cancelSchedule(scheduleId)

      init(schedulingActor)

      testScheduler.state.tasks.isEmpty shouldBe true
    }

    "stop when receiving an upstream failure before being initialised" in new TestContext {
      watch(schedulingActor)

      schedulingActor ! UpstreamFailure(new Exception("boom!"))

      expectTerminated(schedulingActor)
    }

    "schedule new messages at the given time" in new Initialised {
      val (scheduleId, schedule) = generateSchedule

      createSchedule(scheduleId, schedule)

      advanceToTimeFrom(schedule)
      probe.expectMsg(Trigger(scheduleId, schedule))
    }

    "cancel schedules when a cancel message is received" in new Initialised {
      val (scheduleId, schedule) = generateSchedule
      createSchedule(scheduleId, schedule)

      cancelSchedule(scheduleId)

      advanceToTimeFrom(schedule)
      probe.expectNoMessage(NoMsgTimeout)
    }

    "cancel previous schedule when updating an existing schedule" in new Initialised {
      val (scheduleId, schedule) = generateSchedule
      createSchedule(scheduleId, schedule)

      val updatedSchedule = schedule.copy(delay = schedule.delay + 5.minutes)
      createSchedule(scheduleId, updatedSchedule)

      advanceToTimeFrom(schedule)
      probe.expectNoMessage(NoMsgTimeout)

      advanceToTimeFrom(updatedSchedule)
      probe.expectMsg(Trigger(scheduleId, updatedSchedule))
    }

    "stop when receiving an upstream failure" in new Initialised {
      watch(schedulingActor)

      schedulingActor ! UpstreamFailure(new Exception("boom!"))

      expectTerminated(schedulingActor)
    }

    "update monitoring when new schedule is received" in new Initialised {
      val (scheduleId, schedule) = generateSchedule

      createSchedule(scheduleId, schedule)

      eventually {
        scheduleReceivedCounter shouldBe 1L
      }
    }

    "update monitoring when a cancel message is received" in new Initialised {
      val (scheduleId, schedule) = generateSchedule
      createSchedule(scheduleId, schedule)

      cancelSchedule(scheduleId)

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

    def advanceToTimeFrom(schedule: ScheduleEvent): Unit =
      testScheduler.tick(schedule.delay)

    def createSchedule(scheduleId: ScheduleId, schedule: ScheduleEvent): Unit = {
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

  private class Initialised extends TestContext {
    init(schedulingActor)
  }

  def init(actorRef: ActorRef) = {
    actorRef ! Initialised
    expectMsg(Ack)
  }

  private def generateSchedule: (ScheduleId, ScheduleEvent) =
    (UUID.randomUUID().toString, random[ScheduleEvent])

}