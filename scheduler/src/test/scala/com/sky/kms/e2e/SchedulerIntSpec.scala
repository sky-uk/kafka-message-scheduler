package com.sky.kms.e2e

import java.time.OffsetDateTime

import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.domain.*
import com.sky.kms.utils.TestDataUtils.*
import eu.timepit.refined.auto.*
import io.github.embeddedkafka.Codecs.*
import io.github.embeddedkafka.EmbeddedKafkaConfig

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
      println("-" * 90)
      withRunningKafka {
        withSchedulerApp {
          val schedule =
            createSchedules(1, forTopics = List(scheduleTopic), fromNow = -100000L)

          val outputTopicName = schedule.map(_._2.outputTopic).toSet.headOption.getOrElse("Not Topic Name Found")
          println(s">>> Create output topic before publishing: $outputTopicName")
          createCustomTopic(outputTopicName)

          println(s">>> Kafka config: $kafkaConfig")
          println("<BLANK>")
          println(s">>> Created schedule: $schedule")
          println("<BLANK>")

          val published = publish(schedule)
          println(s">>> Published: $published")
          println("<BLANK>")

          published.foreach { _ =>
            val now = OffsetDateTime.now()
            assertMessagesWrittenFrom(OffsetDateTime.now(), schedule)
            println(s">>> assertMessagesWrittenFrom... now: $now || schedule: $schedule")
            println("<BLANK>")
          }

          println(s">>> About to assertTombstoned. Schedule: $schedule")
          println("<BLANK>")
          assertTombstoned(schedule)
        }
      }(kafkaConfig)
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
      val c        = implicitly[EmbeddedKafkaConfig]
      println(s">>> Publish. scheduleEvent.inputTopic: ${scheduleEvent.inputTopic} || id: $id || config: $c")
      println("<BLANK>")
      publishToKafka(scheduleEvent.inputTopic, id, schedule.toAvro)
      schedule.time
    }

    def assertMessagesWrittenFrom(time: OffsetDateTime, schedules: List[(ScheduleId, ScheduleEvent)]): Unit = {
      println(s">>> Schedules: $schedules")
      println("<BLANK>")
      schedules.foreach { case (_, schedule) =>
        println(s">>> Schedule Output Topic: ${schedule.outputTopic}")
        val cr = consumeFirstFrom[Array[Byte]](schedule.outputTopic)

        println(s">>> Consumer Record: $cr")
        println("<BLANK>")
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
    }

    def assertTombstoned(schedules: List[(ScheduleId, ScheduleEvent)]): Unit =
      schedules.groupBy(_._2.inputTopic).foreach { case (topic, schedulesByInputTopic) =>
        val tombstones = consumeSomeFrom[String](topic, schedulesByInputTopic.size * 2).filter(_.value == null)
        tombstones.size shouldBe schedulesByInputTopic.size
        tombstones.map(_.key) shouldBe schedulesByInputTopic.map(_._1).distinct
      }
  }

}
