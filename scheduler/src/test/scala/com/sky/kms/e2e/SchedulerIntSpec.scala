package com.sky.kms.e2e

import cats.Applicative
import com.sky.kms.avro._
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain._
import net.manub.embeddedkafka.Codecs._

class SchedulerIntSpec extends SchedulerIntSpecBase {

  "Scheduler stream" should {
    "schedule a message to be sent to Kafka and delete it after it has been emitted" in new TestContext {
      withSchedulerApp {
        val schedules = createSchedules(2, forTopics = List(scheduleTopic, extraScheduleTopic))
        withRunningKafka {
          publish(schedules)
          assertMessagesWrittenFrom(schedules)
          assertTombstoned(schedules)
        }
      }
    }
  }

  private class TestContext {
    def createSchedules(numSchedules: Int, forTopics: List[String]): List[(ScheduleId, ScheduleEvent)] =
      random[(ScheduleId, ScheduleEvent)](numSchedules).toList.zip(Stream.continually(forTopics.toStream).flatten.take(numSchedules).toList)
        .map { case ((id, schedule), topic) =>
          id -> schedule.copy(inputTopic = topic).secondsFromNow(4)
        }

    def publish(schedules: List[(ScheduleId, ScheduleEvent)]): Unit =
      schedules.foreach { case (id, schedule) => publishToKafka(schedule.inputTopic, id, schedule.toAvro) }

    def assertMessagesWrittenFrom(schedules: List[(ScheduleId, ScheduleEvent)]): Unit =
      schedules.foreach { case (_, schedule) =>
        val cr = consumeFirstFrom[Array[Byte]](schedule.outputTopic)

        cr.key() should contain theSameElementsInOrderAs schedule.key
        cr.value() should contain theSameElementsInOrderAs schedule.value.get
        cr.timestamp() shouldBe schedule.timeInMillis +- Tolerance.toMillis
      }

    def assertTombstoned(schedules: List[(ScheduleId, ScheduleEvent)]): Unit = {
      schedules.groupBy(_._2.inputTopic).foreach { case (topic, schedulesByInputTopic) =>
        val tombstones = consumeSomeFrom[String](topic, schedulesByInputTopic.size * 2).filter(_.value == null)
        tombstones.size shouldBe schedulesByInputTopic.size
        tombstones.map(_.key) shouldBe schedulesByInputTopic.map(_._1).distinct
      }
    }
  }

}
