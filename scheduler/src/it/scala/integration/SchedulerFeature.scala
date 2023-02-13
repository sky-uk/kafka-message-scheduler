package integration

import java.time.OffsetDateTime
import java.util.UUID

import base.IntegrationBase
import com.sky.kms.domain.{ScheduleEvent, ScheduleId}
import com.sky.kms.kafka.Topic
import com.sky.kms.utils.TestDataUtils._
import io.github.embeddedkafka.Codecs.{nullDeserializer, nullSerializer, stringDeserializer, stringSerializer}
import org.scalatest.ConfigMap

import scala.concurrent.duration._

class SchedulerFeature extends IntegrationBase {

  Feature("Scheduler stream") {
    Scenario("schedule a message to be sent to Kafka and delete it after it has been emitted") {
      new TestContext(_) {
        val schedules: List[(ScheduleId, ScheduleEvent)] =
          createSchedules(2, forTopics = allTopics)

        publish(schedules)
          .foreach(assertMessagesWrittenFrom(_, schedules))

        assertTombstoned(schedules)
      }
    }

    Scenario("schedule a delete message if the value of the scheduled message is empty") {
      new TestContext(_) {
        val (scheduleId, scheduleEvent) = createSchedule(scheduleTopic)
        val schedule                    = scheduleEvent.toSchedule.copy(value = None)

        publishToKafka(scheduleTopic.value, scheduleId, schedule.toAvro)

        val cr = consumeFirstFrom[String](schedule.topic)

        cr.key should contain theSameElementsInOrderAs schedule.key
        cr.value shouldBe null
        cr.timestamp shouldBe schedule.timeInMillis +- tolerance.toMillis
      }
    }

    Scenario("continue processing when Kafka becomes available") {
      new TestContext(_) {
        val (beforeId, beforeSchedule) = createSchedule(scheduleTopic)
        val (afterId, afterSchedule)   = createSchedule(scheduleTopic)

        publishToKafka(scheduleTopic.value, beforeId, beforeSchedule.toSchedule.toAvro)

        consumeFirstFrom[Array[Byte]](beforeSchedule.outputTopic)
          .key() should contain theSameElementsInOrderAs beforeSchedule.key

        publishToKafka(scheduleTopic.value, afterId, afterSchedule.secondsFromNow(30).toSchedule.toAvro)

        restartContainer("kafka")

        eventually {
          consumeFirstFrom[Array[Byte]](afterSchedule.outputTopic)
            .key() should contain theSameElementsInOrderAs afterSchedule.key
        }
      }
    }

    Scenario("not schedule messages that have been deleted but not compacted on startup") {
      new TestContext(_) {
        val schedules @ (id, scheduleToDelete) :: otherSchedules = createSchedules(4, List(scheduleTopic), 20)

        val publishedSchedules = publish(schedules)

        delete(id, scheduleToDelete)

        restartContainer("kms")

        publishedSchedules
          .foreach(assertMessagesWrittenFrom(_, otherSchedules))

      }
    }

  }

  private abstract class TestContext(cm: ConfigMap) {
    val tolerance: FiniteDuration = 2000.milliseconds

    def restartContainer(containerName: String): Unit =
      dockerClient.restartContainerCmd(cm(s"$containerName:containerId").toString).exec()

    def createSchedule(forTopic: Topic, seconds: Long = 4): (ScheduleId, ScheduleEvent) = {
      val key = UUID.randomUUID().toString
      key -> random[ScheduleEvent]
        .copy(inputTopic = forTopic.value, outputTopic = "output-" + UUID.randomUUID().toString, key = key.getBytes)
        .secondsFromNow(seconds)
    }

    def createSchedules(
        numSchedules: Int,
        forTopics: List[Topic],
        seconds: Long = 4
    ): List[(ScheduleId, ScheduleEvent)] =
      LazyList
        .continually(forTopics.to(LazyList))
        .flatten
        .take(numSchedules)
        .toList
        .map(createSchedule(_, seconds))

    def publish(scheduleEvents: List[(ScheduleId, ScheduleEvent)]): List[OffsetDateTime] = scheduleEvents.map {
      case (id, scheduleEvent) =>
        val schedule = scheduleEvent.toSchedule
        publishToKafka(scheduleEvent.inputTopic, id, schedule.toAvro)
        schedule.time
    }

    def delete(id: ScheduleId, scheduleEvent: ScheduleEvent): Unit =
      publishToKafka(scheduleEvent.inputTopic, id, null.asInstanceOf[String])

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
