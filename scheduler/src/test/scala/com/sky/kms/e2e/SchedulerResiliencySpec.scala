package com.sky.kms.e2e

import java.util.UUID

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.scaladsl.Source
import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import com.sky.kms.SchedulerApp
import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.config.AppConfig
import com.sky.kms.domain.Schedule
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.zalando.grafter.syntax.rewriter._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SchedulerResiliencySpec extends SchedulerIntBaseSpec with ScalaFutures with RandomDataGenerator {

  implicit val pc =
    PatienceConfig(scaled(Span(10, Seconds)), scaled(Span(500, Millis)))

  "KMS" should {
    "terminate publisher stream when the reader stream fails" in new TestContext with FailingSource {

      withRunningScheduler(app.replace(sourceThatWillFail)) { runningApp =>
        runningApp.runningPublisher.materializedSource.watchCompletion().failed.futureValue shouldBe a[Exception]
      }
    }

    "terminate reader stream when publisher stream fails" in new TestContext {

      withRunningScheduler(app) { runningApp =>
        runningApp.runningPublisher.materializedSource.fail(new Exception("boom!"))
        runningApp.runningReader.materializedSource.isShutdown.futureValue shouldBe Done
      }
    }

    "not terminate the reader stream and keep attempting to publish events until the queue buffer is not full" in new TestContext {

      val randomSchedule = random[Schedule].secondsFromNow(2)

      val sameTimeSchedules = Vector.fill(100)((randomUuid, randomSchedule.toAvro))

      withRunningScheduler(app) { runningApp =>
        writeToKafka(ScheduleTopic, sameTimeSchedules: _*)

        consumeFromKafka(ScheduleTopic, sameTimeSchedules.size, new StringDeserializer)
          .map(_.key()) should contain allElementsOf sameTimeSchedules.map(_._1)
        runningApp.runningReader.materializedSource.isShutdown.isCompleted shouldBe false
      }
    }
  }

  private trait TestContext {

    val app = SchedulerApp.configure apply AppConfig(conf)

    def withRunningScheduler(schedulerApp: SchedulerApp)(scenario: SchedulerApp.Running => Assertion) {
      val runningApp = SchedulerApp.run apply schedulerApp value

      scenario(runningApp)

      CoordinatedShutdown(system).run()
    }

    def randomUuid: String = UUID.randomUUID().toString
  }

  private trait FailingSource {
    this: TestContext =>

    private val stubControl = new Control {
      override def stop() = Future(Done)

      override def shutdown() = Future(Done)

      override def isShutdown = Future(Done)
    }

    val sourceThatWillFail = Source.fromIterator(() => Iterator(Right("someId", None)) ++ (throw new Exception("boom!")))
      .mapMaterializedValue(_ => stubControl)
  }

}
