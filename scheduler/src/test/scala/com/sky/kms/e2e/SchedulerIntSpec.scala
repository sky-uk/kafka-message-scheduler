package com.sky.kms.e2e

import java.time.OffsetDateTime

import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.utils.TestDataUtils._
import com.sky.kms.domain._
import eu.timepit.refined.auto._
import net.manub.embeddedkafka.Codecs._

class SchedulerIntSpec extends SchedulerIntSpecBase {

  "Scheduler stream" should {
    "schedule a message to be sent to Kafka and delete it after it has been emitted" in new TestContext {
      withRunningKafka {
        withSchedulerApp {
          val schedules = createSchedules(2, forTopics = List(scheduleTopic, extraScheduleTopic))

          publish(schedules) andThen
            (assertMessagesWrittenFrom(_, schedules))
          assertTombstoned(schedules)
        }
      }
    }
  }

  private class TestContext {
    def createSchedules(numSchedules: Int, forTopics: List[String]): List[(ScheduleId, ScheduleEvent)] =
      random[(ScheduleId, ScheduleEvent)](numSchedules).toList
        .zip(Stream.continually(forTopics.toStream).flatten.take(numSchedules).toList)
        .map {
          case ((id, schedule), topic) =>
            id -> schedule.copy(inputTopic = topic).secondsFromNow(4)
        }

    def publish: List[(ScheduleId, ScheduleEvent)] => List[OffsetDateTime] = _.map {
      case (id, scheduleEvent) =>
        val schedule = scheduleEvent.toSchedule
        publishToKafka(scheduleEvent.inputTopic, id, schedule.toAvro)
        schedule.time
    }

    def assertMessagesWrittenFrom(time: OffsetDateTime, schedules: List[(ScheduleId, ScheduleEvent)]): Unit =
      schedules.foreach {
        case (_, schedule) =>
          val cr = consumeFirstFrom[Array[Byte]](schedule.outputTopic)

          cr.key should contain theSameElementsInOrderAs schedule.key
          cr.value should contain theSameElementsInOrderAs schedule.value.get
          cr.timestamp shouldBe time.toInstant.toEpochMilli +- Tolerance.toMillis
          cr.headers().toArray.map(h => h.key() -> h.value()).toMap should contain theSameElementsAs schedule.headers
      }

    def assertTombstoned(schedules: List[(ScheduleId, ScheduleEvent)]): Unit =
      schedules.groupBy(_._2.inputTopic).foreach {
        case (topic, schedulesByInputTopic) =>
          val tombstones = consumeSomeFrom[String](topic, schedulesByInputTopic.size * 2).filter(_.value == null)
          tombstones.size shouldBe schedulesByInputTopic.size
          tombstones.map(_.key) shouldBe schedulesByInputTopic.map(_._1).distinct
      }
  }

}
