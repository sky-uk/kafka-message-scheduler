package com.sky.kms.integration

import akka.Done
import akka.kafka.ConsumerFailed
import akka.stream.scaladsl.{Keep, Sink}
import com.sky.kms.base.KafkaIntSpecBase
import com.sky.kms.kafka.KafkaStream._
import com.sky.kms.kafka.{KafkaStream, Topic}
import eu.timepit.refined.auto._
import monix.execution.atomic.AtomicBoolean
import org.scalatest.concurrent.Eventually

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

class KafkaStreamIntSpec extends KafkaIntSpecBase {

  override implicit val patienceConfig = PatienceConfig(5 seconds, 250 millis)

  "source" should {
    "consume and decode messages from Kafka" in new TestContext {
      withRunningKafka {
        publishStringMessageToKafka(testTopic, testMessage)

        val suffix = "-decoded"

        source(Set(testTopic))(system, cr => new String(cr.value) + suffix)
          .runWith(Sink.head)
          .futureValue shouldBe testMessage + suffix
      }
    }

    "fail when Kafka is unavailable" in new TestContext {
      source(Set(testTopic))(system, _.value)
        .runWith(Sink.head)
        .failed
        .futureValue shouldBe a[ConsumerFailed]
    }
  }

  "stream" should {
    "consume from the last committed offset on restart when committing" in new TestContext
    with Eventually {
      withRunningKafka {
        publishStringMessageToKafka(testTopic, "some-msg"))
        val p = Promise[Done]()

        val ctrl = source(Set(testTopic))(system, _.value)
          .via(commitOffset)
          .map(p.trySuccess)
          .toMat(Sink.head)(Keep.left)
          .run

        eventually {
          b.get shouldBe true
          Await.ready(ctrl.shutdown(), 5 seconds)
        }

        publishStringMessageToKafka(testTopic, testMessage)

        val ctrl2 = source(Set(testTopic))(system, _.value)
          .runWith(Sink.head) shouldBe testMessage
      }
    }

    "replay messages in flight when a failure occurs" in {}
  }

  private class TestContext {
    val testTopic: Topic = "test-topic"
    val testMessage = "testMessage"
  }
}
