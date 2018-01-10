package com.sky.kms.e2e

import java.util.UUID

import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import com.sky.kms.SchedulingActor.CreateOrUpdate
import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntBaseSpec
import com.sky.kms.common.TestDataUtils.{random, _}
import com.sky.kms.config._
import com.sky.kms.domain.{Schedule, ScheduleId}
import com.sky.kms.streams.ScheduleReader
import kafka.admin.AdminUtils
import kafka.utils.ZkUtils
import org.scalatest.Assertion

import scala.concurrent.Await

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
    val scheduleReader = ScheduleReader.configure apply AppConfig(conf)
    val (running, _) = scheduleReader.stream(Sink.actorRef(probe.ref, "complete")).run()

    scenario(probe)

    Await.ready(running.shutdown(), conf.shutdownTimeout)
    probe.expectMsg("complete")
  }

  private def writeSchedulesToKafka(schedules: (ScheduleId, Schedule)*) {
    writeToKafka(ScheduleTopic, schedules.map { case (scheduleId, schedule) => (scheduleId, schedule.toAvro) }: _*)
  }

}
