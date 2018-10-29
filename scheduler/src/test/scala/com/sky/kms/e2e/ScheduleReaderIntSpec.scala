package com.sky.kms.e2e

import java.util.UUID

import akka.kafka.ConsumerSettings
import akka.stream.scaladsl.Sink
import akka.testkit.{TestActor, TestProbe}
import com.sky.kms.BackoffRestartStrategy
import com.sky.kms.BackoffRestartStrategy.InfiniteRestarts
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.utils.TestDataUtils._
import com.sky.kms.config._
import com.sky.kms.domain.{ScheduleEvent, ScheduleId}
import com.sky.kms.streams.ScheduleReader
import com.sky.kms.utils.TestActorSystem
import eu.timepit.refined.auto._
import net.manub.embeddedkafka.Codecs.{stringSerializer, nullSerializer => arrayByteSerializer}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually

import scala.collection.JavaConverters._
import scala.concurrent.duration._

class ScheduleReaderIntSpec extends SchedulerIntSpecBase with Eventually {

  override implicit lazy val system = TestActorSystem(kafkaConfig.kafkaPort, maxWakeups = 3, wakeupTimeout = 3.seconds, akkaExpectDuration = 20.seconds)

  val numSchedules = 3

  "stream" should {
    "reload already processed schedules on restart before scheduling" in withRunningKafka {
      val firstSchedule :: newSchedules = List.fill(numSchedules)(generateSchedule)

      withRunningScheduleReader { probe =>
        probe.expectMsg(Initialised)
        writeSchedulesToKafka(firstSchedule)

        probe.expectMsgType[CreateOrUpdate].scheduleId shouldBe firstSchedule._1

        eventually {
          offsetShouldBeCommitted
        }
      }

      withRunningScheduleReader { probe =>
        writeSchedulesToKafka(newSchedules: _*)

        probe.expectMsgType[CreateOrUpdate].scheduleId shouldBe firstSchedule._1
        probe.expectMsg(Initialised)

        val receivedScheduleIds = List.fill(newSchedules.size)(probe.expectMsgType[CreateOrUpdate].scheduleId)

        receivedScheduleIds should contain theSameElementsAs newSchedules.map(_._1)
      }
    }

    "continue processing when Kafka becomes available" in withRunningScheduleReader { probe =>
      withRunningKafka {
        probe.expectMsg(Initialised)
        scheduleShouldFlow(probe)
      }
      withRunningKafka {
        scheduleShouldFlow(probe)
      }
    }
  }

  private def generateSchedule: (ScheduleId, ScheduleEvent) = UUID.randomUUID().toString -> random[ScheduleEvent]

  private def withRunningScheduleReader[T](scenario: TestProbe => T): T = {
    val probe = {
      val p = TestProbe()
      p.setAutoPilot((sender, msg) => msg match {
        case _ =>
          sender ! Ack
          TestActor.KeepRunning
      })
      p
    }

    val killSwitch = ScheduleReader
      .configure(probe.ref)
      .apply(AppConfig(conf))
      .copy(restartStrategy = BackoffRestartStrategy(10.millis, 10.millis, InfiniteRestarts))
      .stream.to(Sink.ignore).run()

    try {
      scenario(probe)
    } finally {
      killSwitch.shutdown()
    }
  }

  private def offsetShouldBeCommitted: Assertion = {
    val consumer = ConsumerSettings(system, new ByteArrayDeserializer, new ByteArrayDeserializer).createKafkaConsumer()
    val partition = new TopicPartition(scheduleTopic, 0)
    consumer.assign(List(partition).asJava)
    consumer.position(partition) should be > 0l
  }

  private def writeSchedulesToKafka(schedules: (ScheduleId, ScheduleEvent)*): Unit =
    publishToKafka(scheduleTopic, schedules.map { case (scheduleId, schedule) => (scheduleId, schedule.toAvro) })

  private def scheduleShouldFlow(probe: TestProbe): SchedulingMessage = {
    writeSchedulesToKafka(generateSchedule)
    probe.expectMsgType[CreateOrUpdate]
  }

}
