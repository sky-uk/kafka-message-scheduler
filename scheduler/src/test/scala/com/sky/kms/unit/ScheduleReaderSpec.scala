package com.sky.kms.unit

import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.testkit.TestPublisher
import akka.testkit.{TestActor, TestProbe}
import akka.{Done, NotUsed}
import cats.syntax.either._
import cats.{Eval, Id}
import com.sky.kms.actors.SchedulingActor
import com.sky.kms.actors.SchedulingActor._
import com.sky.kms.base.AkkaStreamSpecBase
import com.sky.kms.config.ReaderConfig
import com.sky.kms.domain._
import com.sky.kms.streams.ScheduleReader
import com.sky.kms.streams.ScheduleReader.{In, LoadSchedule}
import com.sky.kms.utils.TestDataUtils._
import com.sky.map.commons.akka.streams.BackoffRestartStrategy
import com.sky.map.commons.akka.streams.BackoffRestartStrategy.Restarts
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.boolean.Not
import eu.timepit.refined.numeric.Negative
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}

class ScheduleReaderSpec extends AkkaStreamSpecBase with Eventually {

  override implicit val patienceConfig = PatienceConfig(500.millis, 20.millis)

  "stream" should {
    "send a scheduling message to the scheduling the actor" in new TestContext {
      val schedule@(_, Some(scheduleEvent)) = (random[String], Some(random[ScheduleEvent]))

      runReader()(Source.single(schedule.asRight[ApplicationError]))

      probe.expectMsg(Initialised)
      probe.expectMsgType[CreateOrUpdate].schedule shouldBe scheduleEvent
    }

    "emit errors to the error handler" in new TestContext with ErrorHandler {
      val schedule = random[(String, Some[ScheduleEvent])]

      runReader()(Source.single(random[ApplicationError].asLeft), errorHandler)

      eventually {
        awaitingError.future.isCompleted shouldBe true
      }
    }

    "restore scheduling actor state fully before sending Initialised" in new TestContext {
      val schedules = random[SchedulingMessage](5).toList
      runReader(Source(schedules).mapAsync(1)(_))(Source.empty)

      probe.expectMsgAllOf(schedules: _*)
      probe.expectMsg(Initialised)
    }

    "retry when processing fails" in new TestContext with ProbeSource {
      val schedule = random[(String, Some[ScheduleEvent])]

      runReaderWithProbe()

      pub
        .sendNext(schedule.asRight[ApplicationError])
        .sendError(new Exception("bosh!"))
        .expectSubscription()
    }

    "signal failure to actor when configured number of retries has been reached" in new TestContext with ProbeSource {
      val numRestarts: Int Refined Not[Negative] = 1

      runReaderWithProbe(numRestarts)

      probe.expectMsg(Initialised)

      val error = new Exception("bosh!")

      pub
        .sendNext(random[(String, Some[ScheduleEvent])].asRight[ApplicationError])
        .sendError(error)
        .expectSubscription()
        .sendError(error)

      probe.expectMsgType[CreateOrUpdate]
      probe.expectMsg(UpstreamFailure(error))
    }

    "signal failure to actor when loading processed schedules fails" in new TestContext {
      val ex = new Exception("boom!")
      runReader(Source.failed(ex).mapAsync(1)(_))(Source.empty)

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

    def runReader(init: LoadSchedule => Source[_, _] = _ => Source.empty)(in: Source[In, NotUsed],
                                                                          errorHandler: Sink[ApplicationError, Future[Done]] = Sink.ignore,
                                                                          numRestarts: BackoffRestartStrategy = NoRestarts): Future[Done] =
      ScheduleReader[Id](init,
        Eval.now(in),
        probe.ref,
        Flow[Either[ApplicationError, Ack.type]].map(_ => Done),
        errorHandler,
        numRestarts,
        ReaderConfig.TimeoutConfig(100.millis, 100.millis)).stream.runWith(Sink.ignore)
  }

  private trait ErrorHandler {
    this: TestContext =>

    val awaitingError = Promise[ApplicationError]
    val errorHandler = Sink.foreach[ApplicationError](awaitingError.trySuccess)
  }

  private trait ProbeSource {
    this: TestContext =>

    val pub = TestPublisher.probe[ScheduleReader.In]()

    def runReaderWithProbe(numRestarts: Int Refined Not[Negative] = 1): Future[Done] =
      runReader()(Source.fromPublisher[ScheduleReader.In](pub), numRestarts = NoRestarts.copy(maxRestarts = Restarts(numRestarts)))
  }

}
