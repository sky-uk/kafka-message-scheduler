package com.sky.kms.unit

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.TestPublisher
import akka.testkit.{TestActor, TestProbe}
import akka.{Done, NotUsed}
import cats.Eval
import cats.syntax.either._
import com.sky.kms.actors.SchedulingActor
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.base.AkkaStreamSpecBase
import com.sky.kms.config.ReaderConfig
import com.sky.kms.domain._
import com.sky.kms.streams.ScheduleReader
import com.sky.kms.streams.ScheduleReader.In
import com.sky.kms.utils.TestDataUtils._
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

class ScheduleReaderSpec extends AkkaStreamSpecBase with Eventually {

  override implicit val patienceConfig = PatienceConfig(500.millis, 20.millis)

  "stream" should {
    "send a scheduling message to the scheduling the actor" in new TestContext {
      val schedule@(_, Some(scheduleEvent)) = (random[String], Some(random[ScheduleEvent]))

      runReader(Source.single(schedule.asRight[ApplicationError]))

      probe.expectMsg(StreamStarted)
      probe.expectMsgType[CreateOrUpdate].schedule shouldBe scheduleEvent
    }

    "emit errors to the error handler" in new TestContext with ErrorHandler {
      runReader(Source.single(random[ApplicationError].asLeft), errorHandler)

      eventually {
        awaitingError.future.isCompleted shouldBe true
      }
    }

    "send Initialised to scheduling actor only when source materialized future completes" in new TestContext {
      val p = Promise[Done]()
      runReader(delayedSource, sourceMatFuture = p.future)

      probe.expectMsg(StreamStarted)
      probe.expectMsgType[CreateOrUpdate]

      p.success(Done)
      probe.expectMsg(Initialised)
    }

    "signal failure to actor when stream fails" in new TestContext {
      val error = new Exception("bosh!")
      runReader(Source.failed(error))
      probe.expectMsg(StreamStarted)

      probe.expectMsg(UpstreamFailure(error))
    }

    "signal failure to actor when source materialised future fails" in new TestContext {
      val ex = new Exception("boom!")
      val p = Promise()

      runReader(delayedSource, sourceMatFuture = p.future)
      probe.expectMsg(StreamStarted)

      p.failure(ex)

      probe.expectMsg(UpstreamFailure(ex))
    }
  }

  "toSchedulingMessage" should {
    "generate a CreateOrUpdate message if there is a schedule" in {
      val (scheduleId, schedule) = (random[String], random[ScheduleEvent])
      ScheduleReader.toSchedulingMessage(Right((scheduleId, Some(schedule)))) shouldBe
        Right(SchedulingActor.CreateOrUpdate(scheduleId, schedule))
    }

    "generate a Cancel message if there is no schedule" in {
      val scheduleId = random[String]
      ScheduleReader.toSchedulingMessage(Right((scheduleId, None))) shouldBe
        Right(SchedulingActor.Cancel(scheduleId))
    }
  }

  private class TestContext {
    val msg = random[(String, Some[ScheduleEvent])].asRight[ApplicationError]

    val probe = {
      val p = TestProbe()
      p.setAutoPilot((sender, msg) =>
        msg match {
          case _ =>
            sender ! Ack
            TestActor.KeepRunning
        })
      p
    }

    def delayedSource = Source.tick(100.millis, 100.millis, msg).mapMaterializedValue(_ => NotUsed)

    def runReader(in: Source[In, NotUsed],
                  errorHandler: Sink[ApplicationError, Future[Done]] = Sink.ignore,
                  sourceMatFuture: Future[Done] = Future.never): NotUsed =
      ScheduleReader(
        Eval.now(in.mapMaterializedValue(nu => sourceMatFuture -> nu)),
        probe.ref,
        errorHandler,
        ReaderConfig.TimeoutConfig(100.millis, 100.millis)).stream.run()
  }

  private trait ErrorHandler {
    this: TestContext =>

    val awaitingError = Promise[ApplicationError]
    val errorHandler = Sink.foreach[ApplicationError](awaitingError.trySuccess)
  }

  private trait ProbeSource {
    this: TestContext =>

    val pub = TestPublisher.probe[ScheduleReader.In]()

    def runReaderWithProbe: NotUsed =
      runReader(Source.fromPublisher[ScheduleReader.In](pub))
  }

}
