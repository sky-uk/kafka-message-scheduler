package com.sky.kms.e2e

import java.util.UUID

import akka.stream.scaladsl.Sink
import akka.testkit.{TestActor, TestProbe}
import com.sky.kms.actors.SchedulingActor.{Ack, CreateOrUpdate, Initialised}
import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.config._
import com.sky.kms.domain.{ScheduleEvent, ScheduleId}
import com.sky.kms.streams.ScheduleReader
import eu.timepit.refined.auto._
import net.manub.embeddedkafka.Codecs.{stringSerializer, nullSerializer => arrayByteSerializer}
import org.scalatest.Assertion

import scala.concurrent.duration._

class ScheduleEventReaderIntSpec extends SchedulerIntSpecBase {

  val NumSchedules = 10

  "stream" should {
    "consume from the beginning of the topic on restart" in withRunningKafka {
      createCustomTopic(conf.scheduleTopics.head, partitions = 20, replicationFactor = 1)

      val firstSchedule :: newSchedules = List.fill(NumSchedules)(generateSchedules)

      withRunningScheduleReader { probe =>
        writeSchedulesToKafka(firstSchedule)

        probe
          .expectMsgType[CreateOrUpdate](5 seconds)
          .scheduleId shouldBe firstSchedule._1
      }

      withRunningScheduleReader { probe =>
        writeSchedulesToKafka(newSchedules: _*)

        val allScheduleIds = (firstSchedule :: newSchedules).map {
          case (scheduleId, _) => scheduleId
        }
        val receivedScheduleIds = List.fill(NumSchedules)(
          probe.expectMsgType[CreateOrUpdate](5 seconds).scheduleId)

        receivedScheduleIds should contain theSameElementsAs allScheduleIds
      }
    }
  }

  private def generateSchedules: (ScheduleId, ScheduleEvent) =
    (UUID.randomUUID().toString, random[ScheduleEvent])

  private def withRunningScheduleReader(scenario: TestProbe => Assertion) {
    val probe = TestProbe()

    probe.setAutoPilot((sender, msg) => msg match {
      case _ =>
        sender ! Ack
        TestActor.KeepRunning
    })

    val scheduleReader = ScheduleReader.configure(probe.testActor).apply(AppConfig(conf))
    val killSwitch = scheduleReader.stream.to(Sink.ignore).run()

    probe.expectMsg(Initialised)

    try {
      scenario(probe)
    } finally {
      killSwitch.shutdown()
    }
  }

  private def writeSchedulesToKafka(schedules: (ScheduleId, ScheduleEvent)*): Unit =
    publishToKafka(scheduleTopic, schedules.map { case (scheduleId, schedule) => (scheduleId, schedule.toAvro) })

}
