package com.sky.kms.unit

import java.util.UUID

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.testkit.{TestActor, TestProbe}
import cats.{Eval, Id}
import cats.syntax.either._
import com.sky.kms.actors.SchedulingActor
import com.sky.kms.actors.SchedulingActor.{Ack, CreateOrUpdate, Initialised, SchedulingMessage}
import com.sky.kms.base.AkkaStreamSpecBase
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain._
import com.sky.kms.streams.ScheduleReader
import com.sky.kms.streams.ScheduleReader.{In, LoadSchedule}
import org.scalatest.concurrent.Eventually

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration._

class ScheduleReaderSpec extends AkkaStreamSpecBase with Eventually {

  override implicit val patienceConfig = PatienceConfig(5.seconds, 100.millis)

  "stream" should {
    "send a scheduling message to the scheduling the actor" in new TestContext {
      val schedule@(scheduleId, Some(scheduleEvent)) = (random[String], Some(random[ScheduleEvent]))

      runReader()(Source.single(schedule.asRight[ApplicationError]))

      probe.expectMsg(Initialised)
      probe.expectMsgType[CreateOrUpdate].schedule shouldBe scheduleEvent
    }

    "emit errors to the error handler" in new TestContext {
      val schedule = random[(String, Some[ScheduleEvent])]

      runReader()(Source.single(schedule.asRight[ApplicationError]), errorHandler)

      eventually {
        errorHandlerTriggered.future.isCompleted shouldBe true
      }
    }

    "restore scheduling actor state fully before sending Initialised message" in new TestContext {
      val schedules = random[SchedulingMessage](5).toList
      runReader(Source(schedules).mapAsync(1)(_))(Source.empty)

      probe.expectMsgAllOf(schedules: _*)
      probe.expectMsg(Initialised)
    }
  }

  "toSchedulingMessage" should {
    "generate a CreateOrUpdate message if there is a schedule" in {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[ScheduleEvent])
      ScheduleReader.toSchedulingMessage(Right((scheduleId, Some(schedule)))) shouldBe
        Right(SchedulingActor.CreateOrUpdate(scheduleId, schedule))
    }

    "generate a Cancel message if there is no schedule" in {
      val scheduleId = UUID.randomUUID().toString
      ScheduleReader.toSchedulingMessage(Right((scheduleId, None))) shouldBe
        Right(SchedulingActor.Cancel(scheduleId))
    }
  }

  private class TestContext {
    val probe = TestProbe()

    probe.setAutoPilot((sender, msg) =>
      msg match {
        case _ =>
          sender ! Ack
          TestActor.KeepRunning
      })

    val errorHandlerTriggered = Promise[Either[ApplicationError, Done]]

    val errorHandler: Sink[Either[ApplicationError, Done], Future[Done]] = Sink.foreach(errorHandlerTriggered.trySuccess)

    def runReader(init: LoadSchedule => Source[_, _] = _ => Source.empty)(in: Source[In, NotUsed], errorHandler: Sink[Either[ApplicationError, Done], Future[Done]] = Sink.ignore): Done =
      ScheduleReader[Id, NotUsed](init, Eval.now(in), probe.ref, Flow[Either[ApplicationError, Done]].map(_ => Done), errorHandler).stream.run._2.futureValue
  }

}
