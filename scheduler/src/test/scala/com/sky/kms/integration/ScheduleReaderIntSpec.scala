package com.sky.kms.integration

import java.util.UUID

import akka.actor.ActorSystem
import akka.testkit.{TestActor, TestProbe}
import cats.syntax.functor.*
import com.sky.kms.actors.SchedulingActor.*
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.config.*
import com.sky.kms.domain.{ScheduleEvent, ScheduleId}
import com.sky.kms.streams.ScheduleReader
import com.sky.kms.utils.TestActorSystem
import com.sky.kms.utils.TestDataUtils.*
import eu.timepit.refined.auto.*
import io.github.embeddedkafka.Codecs.{nullSerializer as arrayByteSerializer, stringSerializer}
import io.github.embeddedkafka.EmbeddedKafka
import org.scalatest.BeforeAndAfterEach

import scala.concurrent.Await
import scala.concurrent.duration.*

class ScheduleReaderIntSpec extends SchedulerIntSpecBase with BeforeAndAfterEach {

  override implicit lazy val system: ActorSystem =
    TestActorSystem(kafkaConfig.kafkaPort, akkaExpectDuration = 20.seconds)

  val numSchedules = 3

  override def beforeEach(): Unit = {
    super.beforeEach()
    EmbeddedKafka.start()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    EmbeddedKafka.stop()
  }

  "stream" should {
    "continue processing when Kafka becomes available" in withRunningScheduleReader { probe =>
      probe.expectMsg(StreamStarted)
      probe.expectMsg(Initialised)
      scheduleShouldFlow(probe)

      EmbeddedKafka.stop()
      EmbeddedKafka.start()

      scheduleShouldFlow(probe)
    }

    "not schedule messages that have been deleted but not compacted on startup" in {
      val schedules @ firstSchedule :: _ = List.fill(numSchedules)(generateSchedule)
      writeSchedulesToKafka(schedules*)
      deleteSchedulesInKafka(firstSchedule)

      withRunningScheduleReader { probe =>
        probe.expectMsg(StreamStarted)
        val receivedScheduleIds = List.fill(schedules.size)(probe.expectMsgType[CreateOrUpdate].scheduleId)

        receivedScheduleIds should contain theSameElementsAs schedules.map(_._1)
        probe.expectMsgType[Cancel].scheduleId shouldBe firstSchedule._1
        probe.expectMsg(Initialised)
      }
    }
  }

  private def generateSchedule: (ScheduleId, ScheduleEvent) = UUID.randomUUID().toString -> random[ScheduleEvent]

  private def withRunningScheduleReader[T](scenario: TestProbe => T): T = {
    val probe = {
      val p = TestProbe()
      p.setAutoPilot((sender, msg) =>
        msg match {
          case _ =>
            sender ! Ack
            TestActor.KeepRunning
        }
      )
      p
    }

    val controlF = ScheduleReader
      .configure(probe.ref)
      .apply(AppConfig(conf))
      .stream
      .run()

    try
      scenario(probe)
    finally
      Await.ready(controlF.flatMap(_.shutdown())(system.dispatcher), 5.seconds)
  }

  private def writeSchedulesToKafka(schedules: (ScheduleId, ScheduleEvent)*): Unit =
    publishToKafka(
      scheduleTopic,
      schedules.map { case (scheduleId, scheduleEvent) =>
        (scheduleId, scheduleEvent.toSchedule.toAvro)
      }
    )

  private def scheduleShouldFlow(probe: TestProbe): SchedulingMessage = {
    writeSchedulesToKafka(generateSchedule)
    probe.expectMsgType[CreateOrUpdate]
  }

  private def deleteSchedulesInKafka(schedules: (ScheduleId, ScheduleEvent)*): Unit =
    publishToKafka(scheduleTopic, schedules.map(_.map(_ => null.asInstanceOf[Array[Byte]])))

}
