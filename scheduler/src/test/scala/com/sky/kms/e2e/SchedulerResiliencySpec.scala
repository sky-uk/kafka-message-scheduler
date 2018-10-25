package com.sky.kms.e2e

import java.util.UUID

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestProbe
import cats.syntax.either._
import cats.syntax.option._
import com.sky.kms.avro._
import com.sky.kms.base.{KafkaIntSpecBase, SpecBase}
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.config.{AppConfig, SchedulerConfig}
import com.sky.kms.domain.{ApplicationError, ScheduleEvent}
import com.sky.kms.kafka.KafkaMessage
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import com.sky.kms.utils.{StubControl, StubOffset}
import com.sky.kms.{AkkaComponents, SchedulerApp}
import eu.timepit.refined.auto._
import net.manub.embeddedkafka.Codecs.{stringSerializer, nullDeserializer => arrayByteDeserializer, nullSerializer => arrayByteSerializer}
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SchedulerResiliencySpec extends SpecBase with MockitoSugar {

  override implicit val patienceConfig = PatienceConfig(scaled(10 seconds), scaled(500 millis))

  "KMS" should {
    "terminate when the reader stream fails" in new TestContext with FailingSource with AkkaComponents {
      val app = createAppFrom(config)
        .withReaderSource(sourceThatWillFail)

      withRunningScheduler(app.copy(reader = app.reader.copy(restartStrategy = NoRestarts))) { _ =>
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

    "continue processing when Kafka becomes available" in new KafkaIntSpecBase with TestContext {
      val numSchedules = 5
      val destTopic = random[String]
      val schedules = random[ScheduleEvent](numSchedules).map(_.copy(inputTopic = "some-topic", outputTopic = destTopic).secondsFromNow(1))
      val scheduleIds = List.fill(schedules.size)(UUID.randomUUID().toString)

      withRunningScheduler(createAppFrom(config)) { _ =>
        withRunningKafka {
          publishToKafka(config.scheduleTopics.head, (scheduleIds, schedules.map(_.toAvro)).zipped.toSeq)
        }

        withRunningKafka {
          consumeSomeFrom[Array[Byte]](destTopic, numSchedules).size shouldBe numSchedules
        }
      }
    }
  }

  private trait TestContext {
    val config = SchedulerConfig(Set("some-topic"), queueBufferSize = 100)

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

}
