package com.sky.kms.e2e

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import cats.syntax.option._
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.domain.ScheduleEvent
import com.sky.kms.kafka.AvroBinary
import com.sky.kms.streams.ScheduleReader
import com.sky.kms.utils.TestDataUtils._
import net.manub.embeddedkafka.Codecs.{
  stringSerializer,
  nullDeserializer => arrayByteDeserializer,
  nullSerializer => arrayByteSerializer
}

class SchedulerSchemaEvolutionSpec extends SchedulerIntSpecBase with RandomDataGenerator {

  "scheduler schema" should {

    "be able to decode new schedule events" in new TestContext {
      withRunningKafka {
        withSchedulerApp {
          val scheduleWithHeaders = random[ScheduleEvent]
          val schedule            = scheduleWithHeaders.copy(inputTopic = inputTopic).secondsFromNow(delay)

          val res = publishAndGetDecoded(schedule.inputTopic, schedule.toSchedule.toBinaryAvro)

          res.headerKeys should contain theSameElementsAs scheduleWithHeaders.headerKeys
          res.headerValues should contain theSameElementsAs scheduleWithHeaders.headerValues
        }
      }
    }

    "be able to decode old schedule events" in new TestContext {
      withRunningKafka {
        withSchedulerApp {
          val scheduleNoHeaders = random[ScheduleEventNoHeaders]
          val schedule          = scheduleNoHeaders.copy(inputTopic = inputTopic).secondsFromNow(delay)

          val res = publishAndGetDecoded(schedule.inputTopic, schedule.toScheduleWithoutHeaders.toBinaryAvro)

          res.headers shouldBe Option(Map.empty)
        }
      }
    }

    trait TestContext {

      val inputTopic = "cupcat"
      val delay      = 4

      def publishAndGetDecoded(inputTopic: String, schedule: Array[Byte]) = {
        publishToKafka(inputTopic, inputTopic, schedule)
        consumeSomeFrom[Array[Byte]](inputTopic, 1).headOption.map(AvroBinary.decode)
      }
    }

    implicit class OptionalHeaderOps(val optionalSchedule: Option[ScheduleReader.In]) {
      def headers      = optionalSchedule.flatMap(_.headers)
      def headerKeys   = optionalSchedule.flatMap(_.headerKeys).getOrElse(List.empty)
      def headerValues = optionalSchedule.flatMap(_.headerValues).getOrElse(List.empty)
    }

    implicit class HeaderOps(val schedule: ScheduleReader.In) {
      def headers =
        schedule.fold(_ => none[Map[String, Array[Byte]]], {
          case (_, ose) => ose.fold(none[Map[String, Array[Byte]]])(_.headers.some)
        })
      def headerKeys   = headers.map(_.keys.toList)
      def headerValues = headers.map(_.values.toList.map(_.toList))
    }

  }
}
