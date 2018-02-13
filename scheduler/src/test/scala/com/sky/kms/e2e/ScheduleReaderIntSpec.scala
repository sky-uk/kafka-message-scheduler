package com.sky.kms.e2e

import java.util.UUID

import akka.testkit.{TestActor, TestProbe}
import com.sky.kms.actors.SchedulingActor.{Ack, CreateOrUpdate, Init}
import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.config._
import com.sky.kms.domain.{Schedule, ScheduleId}
import com.sky.kms.streams.ScheduleReader
import kafka.admin.AdminUtils
import kafka.utils.ZkUtils
import org.scalatest.Assertion

import scala.concurrent.Await
import scala.concurrent.duration._

class ScheduleReaderIntSpec extends SchedulerIntBaseSpec {

  val NumSchedules = 10

  lazy val zkUtils = ZkUtils(zkServer, 3000, 3000, isZkSecurityEnabled = false)

  override def afterAll() {
    zkUtils.close()
    super.afterAll()
  }

  "stream" should {
    "consume from the beginning of the topic on restart" in {
      AdminUtils.createTopic(zkUtils, conf.scheduleTopic, partitions = 20, replicationFactor = 1)

      val firstSchedule :: newSchedules = List.fill(NumSchedules)(generateSchedules)

      withRunningScheduleReader { probe =>
        writeSchedulesToKafka(firstSchedule)

        probe.expectMsgType[CreateOrUpdate].scheduleId shouldBe firstSchedule._1
      }

      withRunningScheduleReader { probe =>
        writeSchedulesToKafka(newSchedules: _*)

        val allScheduleIds = (firstSchedule :: newSchedules).map { case (scheduleId, _) => scheduleId }
        val receivedScheduleIds = List.fill(NumSchedules)(probe.expectMsgType[CreateOrUpdate].scheduleId)

        receivedScheduleIds should contain theSameElementsAs allScheduleIds
      }
    }
  }

  private def generateSchedules: (ScheduleId, Schedule) =
    (UUID.randomUUID().toString, random[Schedule])

  private def withRunningScheduleReader(scenario: TestProbe => Assertion) {
    val probe = TestProbe()

    probe.setAutoPilot((sender, msg) => msg match {
      case _ =>
        sender ! Ack
        TestActor.KeepRunning
    })

    val scheduleReader = ScheduleReader.configure(probe.testActor).apply(AppConfig(conf))
    val (running, _) = scheduleReader.stream.run()

    probe.expectMsg(Init)

    scenario(probe)

    Await.ready(running.shutdown(), 5 seconds)
  }

  private def writeSchedulesToKafka(schedules: (ScheduleId, Schedule)*) {
    writeToKafka(ScheduleTopic, schedules.map { case (scheduleId, schedule) => (scheduleId, schedule.toAvro) }: _*)
  }

}
