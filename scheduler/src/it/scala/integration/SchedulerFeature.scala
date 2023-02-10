package integration

import base.IntegrationBase
import com.sky.kms.domain.{ScheduleEvent, ScheduleId}
import com.sky.kms.kafka.Topic
import io.github.embeddedkafka.Codecs.{nullDeserializer, nullSerializer, stringDeserializer, stringSerializer}
import com.sky.kms.utils.TestDataUtils._

import java.time.OffsetDateTime
import scala.concurrent.duration._

class SchedulerFeature extends IntegrationBase {

  Feature("Scheduler stream") {
    Scenario("schedule a message to be sent to Kafka and delete it after it has been emitted") { _ =>
      new TestContext {
        val schedules: List[(ScheduleId, ScheduleEvent)] =
          createSchedules(2, forTopics = List(scheduleTopic, extraScheduleTopic))

        publish(schedules)
          .foreach(assertMessagesWrittenFrom(_, schedules))

        assertTombstoned(schedules)
      }
    }

    Scenario("schedule a delete message if the value of the scheduled message is empty")(_ =>
      new TestContext {
        val scheduleId = random[String]
        val schedule   = random[ScheduleEvent].copy(value = None).secondsFromNow(4).toSchedule

        publishToKafka(scheduleTopic.value, scheduleId, schedule.toAvro)

        val cr = consumeFirstFrom[String](schedule.topic)

        cr.key should contain theSameElementsInOrderAs schedule.key
        cr.value shouldBe null
        cr.timestamp shouldBe schedule.timeInMillis +- tolerance.toMillis
      }
    )

  }

  private trait TestContext {
    val tolerance: FiniteDuration = 2000.milliseconds

    def createSchedules(numSchedules: Int, forTopics: List[Topic]): List[(ScheduleId, ScheduleEvent)] =
      random[(ScheduleId, ScheduleEvent)](numSchedules).toList
        .zip(LazyList.continually(forTopics.to(LazyList)).flatten.take(numSchedules).toList)
        .map { case ((id, schedule), topic) =>
          id -> schedule.copy(inputTopic = topic.value).secondsFromNow(4)
        }

    def publish: List[(ScheduleId, ScheduleEvent)] => List[OffsetDateTime] = _.map { case (id, scheduleEvent) =>
      val schedule = scheduleEvent.toSchedule
      publishToKafka(scheduleEvent.inputTopic, id, schedule.toAvro)
      schedule.time
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
