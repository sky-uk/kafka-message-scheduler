package com.sky.kms.integration

import akka.kafka.ConsumerFailed
import akka.stream.scaladsl.{Keep, Sink}
import cats.data.NonEmptyList
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.kafka.KafkaStream._
import com.sky.kms.kafka.Topic
import eu.timepit.refined.auto._
import org.scalatest.concurrent.Eventually

import scala.concurrent.Await
import scala.concurrent.duration._

class KafkaStreamIntSpec extends SchedulerIntSpecBase with Eventually {

  override implicit val patienceConfig = PatienceConfig(5 seconds, 250 millis)

  "source" should {
    "consume and decode messages from Kafka" in new TestContext {
      withRunningKafka {
        publishStringMessageToKafka(testTopic, testMessage)

        val suffix = "-decoded"

        source[String](NonEmptyList.one(testTopic))(system,
          cr => new String(cr.value) + suffix)
          .runWith(Sink.head)
          .futureValue
          .value shouldBe testMessage + suffix
      }
    }

    "fail when Kafka is unavailable" in new TestContext {
      source(NonEmptyList.one(testTopic))(system, _.value)
        .runWith(Sink.head)
        .failed
        .futureValue shouldBe a[ConsumerFailed]
    }
  }

  "source composed with commitOffset" should {
    "consume from the last committed offset on restart" in new TestContext {
      withRunningKafka {
        publishStringMessageToKafka(testTopic, "some-msg")

        val ctrl = source(NonEmptyList.one(testTopic))(system, _.value)
          .via(commitOffset(conf.reader.offsetBatch))
          .toMat(Sink.head)(Keep.left)
          .run

        eventually {
          Await.ready(ctrl.isShutdown, 5 seconds)
        }

        publishStringMessageToKafka(testTopic, testMessage)

        source[Array[Byte]](NonEmptyList.one(testTopic))(system, _.value)
          .runWith(Sink.head)
          .futureValue
          .value shouldBe testMessage.getBytes
      }
    }

    "replay messages in flight when a failure occurs" in new TestContext {
      withRunningKafka {
        publishStringMessageToKafka(testTopic, testMessage)

        val ctrl = source[Array[Byte]](NonEmptyList.one(testTopic))(
          system, _ =>
            throw new RuntimeException("error occurred processing message."))
          .toMat(Sink.head)(Keep.left)
          .run

        eventually {
          Await.ready(ctrl.isShutdown, 5 seconds)
        }

        source[Array[Byte]](NonEmptyList.one(testTopic))(system, _.value)
          .runWith(Sink.head).futureValue.value shouldBe testMessage.getBytes
      }
    }
  }

  private class TestContext {
    val testTopic: Topic = "test-topic"
    val testMessage = "testMessage"
  }

}
