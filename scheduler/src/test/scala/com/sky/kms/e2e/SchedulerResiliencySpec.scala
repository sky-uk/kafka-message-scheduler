package com.sky.kms.e2e

import java.util.UUID

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestProbe
import cats.Eval
import cats.syntax.either._
import cats.syntax.option._
import com.sky.kms.avro._
import com.sky.kms.base.BaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.config.{AppConfig, SchedulerConfig}
import com.sky.kms.domain.Schedule
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import com.sky.kms.{AkkaComponents, SchedulerApp}
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.zalando.grafter.syntax.rewriter._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SchedulerResiliencySpec extends BaseSpec with ScalaFutures {

  "KMS" should {
    "terminate when the reader stream fails" in new TestContext with FailingSource {
      val app = createAppFrom(config)
        .modifyWith[Any] {
        case reader: ScheduleReader =>
          reader.replace(Eval.later(sourceThatWillFail))
      }

      withRunningScheduler(app) { _ =>
        hasActorSystemTerminated shouldBe true
      }
    }

    "terminate when the publisher stream fails" in new TestContext with IteratingSource {
      val app =
        createAppFrom(config)
          .modifyWith[Any] {
          case reader: ScheduleReader =>
            reader.replace(Eval.later(iteratingSource(createReaderElementsFrom(random[Schedule](n = 10).map(_.secondsFromNow(2))))))
          case publisher: ScheduledMessagePublisher =>
            publisher.replace(Eval.later(Sink.ignore))
        }

      withRunningScheduler(app) { runningApp =>
        runningApp.publisher.materializedSource.fail(new Exception("boom!"))

        hasActorSystemTerminated shouldBe true
      }
    }

    "terminate when the queue buffer becomes full" in new TestContext with IteratingSource {
      val sameTimeSchedules = random[Schedule](n = 20).map(_.secondsFromNow(2))
      val probe = TestProbe()

      val sinkThatWillNotSignalDemand = Sink.actorRefWithAck[ScheduledMessagePublisher.SinkIn](probe.ref, "", "", "")
        .mapMaterializedValue(_ => Future.never)

      val app =
        createAppFrom(config.copy(queueBufferSize = 1))
          .modifyWith[Any] {
          case reader: ScheduleReader =>
            reader.replace(Eval.later(iteratingSource(createReaderElementsFrom(sameTimeSchedules))))
          case publisher: ScheduledMessagePublisher =>
            publisher.replace(Eval.later(sinkThatWillNotSignalDemand))
        }

      withRunningScheduler(app) { _ =>
        hasActorSystemTerminated shouldBe true
      }
    }
  }

  private trait TestContext extends AkkaComponents {

    implicit val patienceConfig = PatienceConfig(scaled(Span(10, Seconds)), scaled(Span(500, Millis)))

    val config = SchedulerConfig("some-topic", 100)

    def createAppFrom(config: SchedulerConfig): SchedulerApp =
      SchedulerApp.configure apply AppConfig(config)

    def withRunningScheduler(schedulerApp: SchedulerApp)(scenario: SchedulerApp.Running => Assertion) {
      val runningApp = SchedulerApp.run apply schedulerApp

      scenario(runningApp)

      CoordinatedShutdown(system).run()
    }

    def hasActorSystemTerminated: Boolean =
      Await.ready(system.whenTerminated, 10 seconds).isCompleted
  }

  private val stubControl = new Control {
    override def stop() = Future(Done)

    override def shutdown() = Future(Done)

    override def isShutdown = Future(Done)
  }

  private trait FailingSource {
    this: TestContext =>

    val sourceThatWillFail = Source.fromIterator(() => Iterator(Right("someId", None)) ++ (throw new Exception("boom!")))
      .mapMaterializedValue(_ => stubControl)
  }

  private trait IteratingSource {
    this: TestContext =>

    def createReaderElementsFrom(schedules: Seq[Schedule]): Iterator[ScheduleReader.In] = {
      val scheduleIds = List.fill(schedules.size)(UUID.randomUUID().toString)

      (scheduleIds, schedules.map(_.some)).zipped.toIterator.map(_.asRight)
    }


    def iteratingSource(elements: Iterator[ScheduleReader.In]) =
      Source.fromIterator(() => elements).mapMaterializedValue(_ => stubControl)
  }

}
