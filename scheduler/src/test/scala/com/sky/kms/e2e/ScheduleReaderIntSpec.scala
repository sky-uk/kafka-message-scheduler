package com.sky.kms.e2e

import java.util.UUID

import akka.stream.scaladsl.Sink
import akka.testkit.TestProbe
import com.sky.kms.SchedulingActor.CreateOrUpdate
import com.sky.kms.avro._
import com.sky.kms.common.TestDataUtils.{random, _}
import com.sky.kms.common.{AkkaStreamBaseSpec, KafkaIntSpec}
import com.sky.kms.config._
import com.sky.kms.domain.{Schedule, ScheduleId}
import com.sky.kms.kafka.KafkaStream
import com.sky.kms.streams.ScheduleReader
import kafka.admin.AdminUtils
import kafka.utils.ZkUtils
import org.zalando.grafter.StopOk

import scala.concurrent.duration._
import com.sky.kms.common.EmbeddedKafka._

class ScheduleReaderIntSpec extends AkkaStreamBaseSpec with KafkaIntSpec {

  val ScheduleTopic = "scheduleTopic"

  val conf = SchedulerConfig(ScheduleTopic, ShutdownTimeout(10 seconds, 10 seconds), 100)

  "stream" should {
    "consume from the beginning of the topic when it restarts" in {
//      BUG!
//      AdminUtils.createTopic(ZkUtils(s"localhost:${kafkaServer.zookeeperPort}", 3000, 3000, false), conf.scheduleTopic, 20, 1)

      val probe = TestProbe()
      val reader = ScheduleReader(conf, KafkaStream.source(conf), Sink.actorRef(probe.ref, "complete"))

      reader.stream()
      val firstScheduleId = writeScheduleToKafka

      probe.expectMsgType[CreateOrUpdate].scheduleId shouldBe firstScheduleId
      reader.stop.value shouldBe a[StopOk]
      probe.expectMsg("complete")

      reader.stream()
      //without this we consume the second and third messages twice :S BECAUSE WHY NOT
      Thread.sleep(2000)

      val secondScheduleId = writeScheduleToKafka
      val thirdScheduleId = writeScheduleToKafka

      probe.expectMsgType[CreateOrUpdate].scheduleId shouldBe firstScheduleId
      probe.expectMsgType[CreateOrUpdate].scheduleId shouldBe secondScheduleId
      probe.expectMsgType[CreateOrUpdate].scheduleId shouldBe thirdScheduleId
      reader.stop.value shouldBe a[StopOk]
      probe.expectMsg("complete")

      system.stop(probe.ref)
    }
  }

  private def writeScheduleToKafka: ScheduleId = {
    val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule])
    writeToKafka(ScheduleTopic, scheduleId, schedule.toAvro)
    scheduleId
  }

}
