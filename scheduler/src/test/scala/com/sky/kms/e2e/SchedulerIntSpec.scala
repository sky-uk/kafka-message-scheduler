package com.sky.kms.e2e

import java.time.OffsetDateTime

import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.domain.*
import com.sky.kms.utils.TestDataUtils.*
import eu.timepit.refined.auto.*
import io.github.embeddedkafka.Codecs.*

class SchedulerIntSpec extends SchedulerIntSpecBase {

  "Scheduler stream" should {
    "schedule a message to be sent to Kafka and delete it after it has been emitted" in new TestContext {
      withRunningKafka {
        withSchedulerApp {
          val schedules = createSchedules(2, forTopics = List(scheduleTopic, extraScheduleTopic))

          publish(schedules)
            .foreach(assertMessagesWrittenFrom(_, schedules))

          assertTombstoned(schedules)
        }
      }
    }

    "schedule a past message to be sent to Kafka immediately and delete it after it has been emitted" in new TestContext {
      withRunningKafka {
        withSchedulerApp {
          val schedule =
            createSchedules(1, forTopics = List(scheduleTopic, extraScheduleTopic), fromNow = -100000L)

          publish(schedule)
            .foreach(_ => assertMessagesWrittenFrom(OffsetDateTime.now(), schedule))

          assertTombstoned(schedule)
        }
      }
    }

  }

  private class TestContext {
    def createSchedules(
        numSchedules: Int,
        forTopics: List[String],
        fromNow: Long = 4
    ): List[(ScheduleId, ScheduleEvent)] =
      random[(ScheduleId, ScheduleEvent)](numSchedules).toList
        .zip(LazyList.continually(forTopics.to(LazyList)).flatten.take(numSchedules).toList)
        .map { case ((id, schedule), topic) =>
          id -> schedule.copy(inputTopic = topic).secondsFromNow(fromNow)
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
