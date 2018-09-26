package com.sky.kms.e2e

import java.util.UUID

import akka.Done
import akka.actor.CoordinatedShutdown.UnknownReason
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestProbe
import cats.syntax.either._
import cats.syntax.option._
import com.sky.kms.avro._
import com.sky.kms.base.BaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.common.{EmbeddedKafka, TestActorSystem}
import com.sky.kms.config.{AppConfig, SchedulerConfig}
import com.sky.kms.domain.ScheduleEvent
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import com.sky.kms.{AkkaComponents, SchedulerApp}
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SchedulerResiliencySpec extends BaseSpec with ScalaFutures {

  "KMS" should {
    "terminate when the reader stream fails" in new TestContext with FailingSource with AkkaComponents {
      val app = createAppFrom(config)
        .withReaderSource(sourceThatWillFail)

      withRunningScheduler(app) { _ =>
        hasActorSystemTerminated shouldBe true
      }
    }

    "terminate when the publisher stream fails" in new TestContext with IteratingSource with AkkaComponents {
      val app =
        createAppFrom(config)
          .withReaderSource(sourceWith(random[ScheduleEvent](n = 10).map(_.secondsFromNow(2))))
          .withPublisherSink(Sink.ignore)

      withRunningScheduler(app) { runningApp =>
        runningApp.publisher.materializedSource.fail(new Exception("boom!"))

        hasActorSystemTerminated shouldBe true
      }
    }

    "terminate when the queue buffer becomes full" in new TestContext with IteratingSource with AkkaComponents {
      val sameTimeSchedules = random[ScheduleEvent](n = 20).map(_.secondsFromNow(2))
      val probe = TestProbe()
      val sinkThatWillNotSignalDemand = Sink.actorRefWithAck[ScheduledMessagePublisher.SinkIn](probe.ref, "", "", "")
        .mapMaterializedValue(_ => Future.never)

      val app =
        createAppFrom(config.copy(queueBufferSize = 1))
          .withReaderSource(sourceWith(sameTimeSchedules))
          .withPublisherSink(sinkThatWillNotSignalDemand)

      withRunningScheduler(app) { _ =>
        hasActorSystemTerminated shouldBe true
      }
    }

    "terminate when Kafka goes down during processing" in new KafkaTestContext {
      val distantSchedules = random[ScheduleEvent](n = 100).map(_.secondsFromNow(60))
      val scheduleIds = List.fill(distantSchedules.size)(UUID.randomUUID().toString)

      kafkaServer.startup()
      withRunningScheduler(createAppFrom(config)) { _ =>
        writeToKafka(topic = config.scheduleTopic.head,
          keyValues = (scheduleIds, distantSchedules.map(_.toAvro)).zipped.toSeq: _*)
        kafkaServer.close()

        hasActorSystemTerminated shouldBe true
      }
    }

    "terminate when Kafka is unavailable at startup" in new KafkaTestContext {
      withRunningScheduler(createAppFrom(config)) { _ =>
        hasActorSystemTerminated shouldBe true
      }
    }
  }

  private trait TestContext {

    implicit val patienceConfig = PatienceConfig(scaled(10 seconds), scaled(500 millis))

    val config = SchedulerConfig(Set("some-topic"), 100)

    def createAppFrom(config: SchedulerConfig)(implicit system: ActorSystem): SchedulerApp =
      SchedulerApp.configure apply AppConfig(config)

    def withRunningScheduler(schedulerApp: SchedulerApp)(scenario: SchedulerApp.Running => Assertion)(implicit system: ActorSystem, mat: ActorMaterializer) {
      val runningApp = SchedulerApp.run apply schedulerApp

      scenario(runningApp)

      CoordinatedShutdown(system).run(UnknownReason)
    }

    def hasActorSystemTerminated(implicit system: ActorSystem): Boolean =
      Await.ready(system.whenTerminated, 10 seconds).isCompleted
  }

  private val stubControl = new Control {
    override def stop() = Future(Done)

    override def shutdown() = Future(Done)

    override def isShutdown = Future(Done)
  }

  private trait FailingSource {
    this: TestContext =>

    val sourceThatWillFail =
      Source.fromIterator(() => Iterator(Right("someId", None)) ++ (throw new Exception("boom!")))
        .mapMaterializedValue(_ => stubControl)
  }

  private trait IteratingSource {
    this: TestContext =>

    def sourceWith(schedules: Seq[ScheduleEvent]): Source[ScheduleReader.In, ScheduleReader.Mat] = {
      val scheduleIds = List.fill(schedules.size)(UUID.randomUUID().toString)

      val elements = (scheduleIds, schedules.map(_.some)).zipped.toIterator.map(_.asRight)

      Source.fromIterator(() => elements).mapMaterializedValue(_ => stubControl)
    }
  }

  private class KafkaTestContext extends TestContext with EmbeddedKafka with AkkaComponents {
    override implicit lazy val system: ActorSystem = TestActorSystem(kafkaServer.kafkaPort, terminateActorSystem = true)
  }

}
