package com.sky.kms.integration

import akka.kafka.ConsumerFailed
import akka.stream.scaladsl.Sink
import cats.data.NonEmptyList
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.kafka.KafkaStream._
import com.sky.kms.kafka.Topic
import eu.timepit.refined.auto._
import org.scalatest.concurrent.Eventually

import scala.concurrent.duration._

class KafkaStreamIntSpec extends SchedulerIntSpecBase with Eventually {

  override implicit val patienceConfig = PatienceConfig(5 seconds, 250 millis)

  val testTopic: Topic = "test-topic"
  val testMessage = "testMessage"

  "source" should {
    "consume and decode messages from Kafka" in {
      withRunningKafka {
        publishStringMessageToKafka(testTopic, testMessage)

        val suffix = "-decoded"

        source[String](NonEmptyList.one(testTopic))(system,
          cr => new String(cr.value) + suffix)
          .runWith(Sink.head)
          .futureValue shouldBe testMessage + suffix
      }
    }

    "fail when Kafka is unavailable" in {
      source(NonEmptyList.one(testTopic))(system, _.value)
        .runWith(Sink.head)
        .failed
        .futureValue shouldBe a[ConsumerFailed]
    }
  }

}
