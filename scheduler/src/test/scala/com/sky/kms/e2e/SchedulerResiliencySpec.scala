package com.sky.kms.e2e

import java.util.UUID

import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.CommittableOffset
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestProbe
import cats.syntax.either._
import cats.syntax.option._
import com.sky.kms.avro._
import com.sky.kms.base.{KafkaIntSpecBase, SpecBase}
import com.sky.kms.common.TestActorSystem
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.config.{AppConfig, SchedulerConfig}
import com.sky.kms.domain.{ApplicationError, ScheduleEvent, ScheduleId}
import com.sky.kms.kafka.KafkaMessage
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import com.sky.kms.utils.{StubControl, StubOffset}
import com.sky.kms.{AkkaComponents, SchedulerApp}
import eu.timepit.refined.auto._
import net.manub.embeddedkafka.Codecs.{nullSerializer, stringSerializer}
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SchedulerResiliencySpec extends SpecBase with MockitoSugar {

  override implicit val patienceConfig = PatienceConfig(scaled(10 seconds), scaled(500 millis))

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

      withRunningKafka {
        withRunningScheduler(createAppFrom(config)) { _ =>
          publishToKafka(config.scheduleTopics.head, (scheduleIds, distantSchedules.map(_.toAvro)).zipped.toSeq)
        }
      }
      hasActorSystemTerminated shouldBe true
    }

    "terminate when Kafka is unavailable at startup" in new KafkaTestContext {
      withRunningScheduler(createAppFrom(config)) { _ =>
        hasActorSystemTerminated shouldBe true
      }
    }
  }

  private trait TestContext {
    val config = SchedulerConfig(Set("some-topic"), 100)

    def createAppFrom(config: SchedulerConfig)(implicit system: ActorSystem): SchedulerApp =
      SchedulerApp.configure apply AppConfig(config)

    def hasActorSystemTerminated(implicit system: ActorSystem): Boolean =
      Await.ready(system.whenTerminated, 10 seconds).isCompleted
  }

  private trait FailingSource {
    this: TestContext =>

    import cats.syntax.either._
    import cats.syntax.option._

    val sourceThatWillFail: Source[KafkaMessage[ScheduleReader.In], Control] =
      Source.fromIterator(() => Iterator(KafkaMessage(StubOffset(), ("someId", none[ScheduleEvent]).asRight[ApplicationError])) ++ (throw new Exception("boom!")))
        .mapMaterializedValue(_ => StubControl())
  }

  private trait IteratingSource {
    this: TestContext =>

    def sourceWith(schedules: Seq[ScheduleEvent]): Source[KafkaMessage[ScheduleReader.In], Control] = {
      val scheduleIds = List.fill(schedules.size)(UUID.randomUUID().toString)

      val elements = (scheduleIds, schedules.map(_.some)).zipped.toIterator.map(_.asRight[ApplicationError]).toList

      Source(elements.map(KafkaMessage(StubOffset(), _))).mapMaterializedValue(_ => StubControl())
    }
  }

  private class KafkaTestContext extends KafkaIntSpecBase with TestContext {
    override implicit lazy val system: ActorSystem = TestActorSystem(kafkaConfig.kafkaPort, terminateActorSystem = true)
  }

}
