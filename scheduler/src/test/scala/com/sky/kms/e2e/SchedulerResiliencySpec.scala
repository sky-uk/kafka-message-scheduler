package com.sky.kms.e2e

import java.util.UUID

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.scaladsl.Source
import com.sky.kms.avro._
import com.sky.kms.base.BaseSpec
import com.sky.kms.common.EmbeddedKafka
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.config.{AppConfig, SchedulerConfig}
import com.sky.kms.domain.Schedule
import com.sky.kms.{AkkaComponents, SchedulerApp}
import org.apache.kafka.common.serialization.StringDeserializer
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.zalando.grafter.syntax.rewriter._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class SchedulerResiliencySpec extends BaseSpec with ScalaFutures {

  "KMS" should {
    "terminate when the reader stream fails" in new TestContext with FailingSource {
      withRunningScheduler(app.replace(sourceThatWillFail)) { runningApp =>
        val stopped = for {
          _ <- system.whenTerminated
          _ <- runningApp.runningPublisher.materializedSource.watchCompletion()
        } yield ()

        Await.ready(stopped, 5 seconds).isCompleted shouldBe true
      }
    }

    "terminate when the publisher stream fails" in new TestContext with EmbeddedKafka {
      withRunningKafka {
        withRunningScheduler(app) { runningApp =>
          runningApp.runningPublisher.materializedSource.fail(exception)
          val stopped = for {
            _ <- runningApp.runningReader.materializedSource.isShutdown
            _ <- system.whenTerminated
          } yield ()

          Await.ready(stopped, 5 seconds).isCompleted shouldBe true
        }
      }
    }

    "keep attempting to publish events until the queue buffer is not full" in new TestContext with EmbeddedKafka {
      val randomSchedule = random[Schedule].secondsFromNow(2)

      val sameTimeSchedules = Vector.fill(100)((randomUuid, randomSchedule.toAvro))

      withRunningKafka {
        withRunningScheduler(app) { runningApp =>
          writeToKafka(ScheduleTopic, sameTimeSchedules: _*)

          val keysInKafka = consumeFromKafka(ScheduleTopic, sameTimeSchedules.size, new StringDeserializer).map(_.key)

          keysInKafka should contain allElementsOf sameTimeSchedules.map(_._1)
          runningApp.runningReader.materializedSource.isShutdown.isCompleted shouldBe false
        }
      }
    }
  }

  private trait TestContext extends AkkaComponents {

    implicit val patienceConfig = PatienceConfig(scaled(Span(10, Seconds)), scaled(Span(500, Millis)))

    val ScheduleTopic = "schedules"

    val exception = new Exception("boom!")
    val conf = SchedulerConfig(ScheduleTopic, 10 seconds, 1)
    val app = SchedulerApp.configure apply AppConfig(conf)

    def withRunningScheduler(schedulerApp: SchedulerApp)(scenario: SchedulerApp.Running => Assertion) {
      val runningApp = SchedulerApp.run apply schedulerApp

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

    val sourceThatWillFail = Source.fromIterator(() => Iterator(Right("someId", None)) ++ (throw exception))
      .mapMaterializedValue(_ => stubControl)
  }

}
