package com.sky.kafka.message.scheduler

import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.stream.scaladsl.SourceQueue
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.miguno.akka.testing.VirtualTime
import com.sky.kafka.message.scheduler.SchedulingActor.{Ack, CancelSchedule, CreateOrUpdateSchedule}
import com.sky.kafka.message.scheduler.TestDataUtils._
import com.sky.kafka.message.scheduler.domain.Schedule
import common.{BaseSpec, TestActorSystem}
import org.mockito.Mockito
import org.scalatest.mockito.MockitoSugar
import Mockito._

import scala.concurrent.duration._


class SchedulingActorSpec extends TestKit(TestActorSystem()) with ImplicitSender with BaseSpec with MockitoSugar {

  case class UnexpectedMessageType(whatever: String)

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  private class SchedulingActorTest {
    val mockSourceQueue = mock[SourceQueue[(String, Schedule)]]
    val time = new VirtualTime

    val actorRef = TestActorRef(new SchedulingActor(mockSourceQueue, time.scheduler))

    def advanceTimeTo(offsetDateTime: OffsetDateTime) =
      time.advance(offsetDateTime.toInstant.toEpochMilli - System.currentTimeMillis() + 1 second)

    def createSchedule(scheduleId: String, schedule: Schedule) = {
      actorRef ! CreateOrUpdateSchedule(scheduleId, schedule)
      expectMsg(Ack)
    }
  }

  "A scheduler actor" must {
    "schedule new messages at the given time" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule

      createSchedule(scheduleId, schedule)

      advanceTimeTo(schedule.time)
      verify(mockSourceQueue).offer((scheduleId, schedule))
    }

    "cancel schedules when a cancel message is received" in new SchedulingActorTest {
      val (scheduleId, schedule) = generateSchedule

      createSchedule(scheduleId, schedule)

      actorRef ! CancelSchedule(scheduleId)
      expectMsg(Ack)

      advanceTimeTo(schedule.time)
      verifyZeroInteractions(mockSourceQueue)
    }

  }

  private def generateSchedule =
    (UUID.randomUUID().toString, random[Schedule].copy(time = OffsetDateTime.now().plusMinutes(5)))
}