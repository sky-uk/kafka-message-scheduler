package com.sky.kms.e2e

import java.util.UUID

import akka.Done
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.scaladsl.Source
import com.sky.kms.SchedulerApp
import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.config.AppConfig
import com.sky.kms.domain.Schedule
import com.sky.kms.streams.ScheduledMessagePublisher
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.zalando.grafter.syntax.rewriter._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

class SchedulerResiliencySpec extends SchedulerIntBaseSpec with ScalaFutures {

  implicit val pc =
    PatienceConfig(scaled(Span(10, Seconds)), scaled(Span(500, Millis)))

  "KMS" should {
    "terminate publisher stream when the reader stream fails" in new TestContext {

      withRunningScheduler(app.replace[Source[_, Control]](sourceThatWillFail)) { app =>
        app.runningPublisher.watchCompletion().failed.futureValue shouldBe a[Exception]
      }
    }

    "terminate reader stream when publisher stream fails" in new TestContext {

      withRunningScheduler(app) { running =>
        causePublisherToFail(running.runningPublisher)
        running.runningReader.isShutdown.futureValue shouldBe Done
      }
    }
  }

  private class TestContext {

    val app = SchedulerApp.configure apply AppConfig(conf)

    def withRunningScheduler(schedulerApp: SchedulerApp)(scenario: SchedulerApp.Running => Assertion) {
      val runningApp = SchedulerApp.run apply schedulerApp value

      scenario(runningApp)

      Await.ready(SchedulerApp.stop apply runningApp, conf.shutdownTimeout)
    }

    val sourceThatWillFail = Source.fromIterator(() => Iterator(Right("someId", None)) ++ (throw new Exception("boom!")))
      .mapMaterializedValue(_ =>
        new Control {
          override def stop() = Future(Done)

          override def shutdown() = Future(Done)

          override def isShutdown = Future(Done)
        })

    def causePublisherToFail(runningPublisher: ScheduledMessagePublisher.Mat) {
      val (scheduleId, schedule) =
        (UUID.randomUUID().toString, random[Schedule].secondsFromNow(2))
      runningPublisher.fail(new Exception("boom!"))
      writeToKafka(ScheduleTopic, (scheduleId, schedule.toAvro))
    }
  }

}
