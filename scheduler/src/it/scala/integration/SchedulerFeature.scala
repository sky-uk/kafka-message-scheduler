package integration

import base.IntegrationBase
import com.sky.kms.domain.{ScheduleEvent, ScheduleId}
import com.sky.kms.kafka.Topic
import io.github.embeddedkafka.Codecs.{nullDeserializer, nullSerializer, stringDeserializer, stringSerializer}
import com.sky.kms.utils.TestDataUtils._
import org.scalatest.ConfigMap

import java.time.OffsetDateTime
import scala.concurrent.duration._

class SchedulerFeature extends IntegrationBase {

  Feature("Scheduler stream") {
//    Scenario("schedule a message to be sent to Kafka and delete it after it has been emitted") {
//      new TestContext(_) {
//        val schedules: List[(ScheduleId, ScheduleEvent)] =
//          createSchedules(2, forTopics = List(scheduleTopic, extraScheduleTopic))
//
//        publish(schedules)
//          .foreach(assertMessagesWrittenFrom(_, schedules))
//
//        assertTombstoned(schedules)
//      }
//    }

//    Scenario("schedule a delete message if the value of the scheduled message is empty") {
//      new TestContext(_) {
//        val scheduleId = random[String]
//        val schedule   = random[ScheduleEvent].copy(value = None).secondsFromNow(4).toSchedule
//
//        publishToKafka(scheduleTopic.value, scheduleId, schedule.toAvro)
//
//        val cr = consumeFirstFrom[String](schedule.topic)
//
//        cr.key should contain theSameElementsInOrderAs schedule.key
//        cr.value shouldBe null
//        cr.timestamp shouldBe schedule.timeInMillis +- tolerance.toMillis
//      }
//    }

    Scenario("continue processing when Kafka becomes available") {
      new TestContext(_) {
        val (scheduleId, schedule) = createSchedules(1, forTopics = allTopics).head

        schedule.copy(inputTopic = scheduleTopic.value, outputTopic = "foobar")

        publishAndAssertConsumption(scheduleId, schedule)

//        dockerClient.restartContainerCmd(kafkaContainerId).exec()

//        publishAndAssertConsumption(scheduleId, schedule)
      }
    }

  }

  private abstract class TestContext(cm: ConfigMap) {
    val tolerance: FiniteDuration = 2000.milliseconds

    val consumerGroup    = "com.sky.kafka.scheduler"
    val kafkaContainerId = cm(s"kafka:containerId").toString
    println(s"Kafka container ID: $kafkaContainerId")

    def createSchedules(numSchedules: Int, forTopics: List[Topic]): List[(ScheduleId, ScheduleEvent)] =
      random[(ScheduleId, ScheduleEvent)](numSchedules).toList
        .zip(LazyList.continually(forTopics.to(LazyList)).flatten.take(numSchedules).toList)
        .map { case ((id, schedule), topic) =>
          id -> schedule.copy(inputTopic = topic.value).secondsFromNow(4)
        }

    def publish(scheduleEvents: List[(ScheduleId, ScheduleEvent)]): List[OffsetDateTime] = scheduleEvents.map {
      case (id, scheduleEvent) =>
        val schedule = scheduleEvent.toSchedule
        publishToKafka(scheduleEvent.inputTopic, id, schedule.toAvro)
        schedule.time
    }

    def publishAndAssertConsumption(scheduleId: ScheduleId, scheduleEvent: ScheduleEvent): Unit = {
      val currentOffset = offsetPosition(consumerGroup, scheduleEvent.inputTopic)
      println(s"Current offset: $currentOffset")
      val schedule      = scheduleEvent.toSchedule
      println(s"> Publishing to topic: ${scheduleEvent.inputTopic}")
      publishToKafka(scheduleEvent.inputTopic, scheduleId, schedule.toAvro)
      eventually {
        val newOffset = offsetPosition(consumerGroup, scheduleEvent.inputTopic)
        newOffset should be > currentOffset
      }
    }

    def assertMessagesWrittenFrom(time: OffsetDateTime, schedules: List[(ScheduleId, ScheduleEvent)]): Unit =
      schedules.foreach { case (_, schedule) =>
        val cr = consumeFirstFrom[Array[Byte]](schedule.outputTopic)

        cr.key should contain theSameElementsInOrderAs schedule.key

        schedule.value match {
          case Some(value) => cr.value should contain theSameElementsAs value
          case None        => cr.value shouldBe null
        }

        cr.timestamp shouldBe time.toInstant.toEpochMilli +- tolerance.toMillis
        cr.headers().toArray.map(h => h.key() -> h.value().toList) should contain theSameElementsAs
          schedule.headers.map { case (k, v) =>
            (k, v.toList)
          }
      }

    def assertTombstoned(schedules: List[(ScheduleId, ScheduleEvent)]): Unit =
      schedules.groupBy(_._2.inputTopic).foreach { case (topic, schedulesByInputTopic) =>
        val tombstones = consumeSomeFrom[String](topic, schedulesByInputTopic.size * 2).filter(_.value == null)
        tombstones.size shouldBe schedulesByInputTopic.size
        tombstones.map(_.key) shouldBe schedulesByInputTopic.map(_._1).distinct
      }
  }
}
