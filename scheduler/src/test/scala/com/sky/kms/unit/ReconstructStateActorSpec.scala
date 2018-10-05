package com.sky.kms.unit

import java.util.UUID

import akka.testkit.TestProbe
import com.sky.kms.actors.ReconstructStateActor
import com.sky.kms.actors.ReconstructStateActor._
import com.sky.kms.base.AkkaBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain.{Schedule, ScheduleId}
import org.scalatest.concurrent.ScalaFutures

class ReconstructStateActorSpec extends AkkaBaseSpec with ScalaFutures {

  "A reconstructing state actor" must {
    "store all schedules yet to be scheduled" in new TestContext {
      val scheduleYetToFire = generateSchedule

      val schedules = scheduleYetToFire :: List
        .fill(2)(generateScheduleAndDelete)
        .flatten

      processSchedules(schedules)

      stateActor.tell(SchedulesToBeProcessed, probe.ref)

      probe expectMsg Map[ScheduleId, Schedule](scheduleYetToFire)
    }
  }

  private class TestContext {

    val probe = TestProbe()
    val stateActor = ReconstructStateActor.create()

    def generateSchedule: (ScheduleId, Schedule) =
      (UUID.randomUUID().toString, random[Schedule])

    def generateScheduleAndDelete: List[(ScheduleId, Schedule)] = {
      val scheduleEvent = random[Schedule]

      List((UUID.randomUUID().toString, scheduleEvent),
           (UUID.randomUUID().toString, scheduleEvent.copy(value = None)))
    }

    def processSchedules(schedules: List[(ScheduleId, Schedule)]): Unit = {
      schedules.foreach {
        case (id, event) => stateActor ! ProcessSchedule(id, event)
      }
    }
  }
}
