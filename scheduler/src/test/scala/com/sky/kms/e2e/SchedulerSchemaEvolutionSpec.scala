package com.sky.kms.e2e

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import cats.syntax.option._
import com.sky.kms._
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.domain.ScheduleEvent
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
          val sched               = scheduleWithHeaders.copy(inputTopic = "cupcat").secondsFromNow(4)

          publishToKafka(sched.inputTopic, "cupcat", sched.toSchedule.toAvro)

          val published = consumeSomeFrom[Array[Byte]]("cupcat", 1).headOption

          published shouldBe defined
          val res = scheduleConsumerRecordDecoder(published.get)
          res.headerKeys shouldBe Option(scheduleWithHeaders.headers.map(_._1))
          res.headerValues shouldBe Option(scheduleWithHeaders.headers.map(_._2.toList))
        }
      }
    }

    "be able to decode old schedule events" in new TestContext {
      withRunningKafka {
        withSchedulerApp {
          val scheduleNoHeaders = random[ScheduleEventNoHeaders]
          val sched             = scheduleNoHeaders.copy(inputTopic = "cupcat").secondsFromNow(4)

          publishToKafka(sched.inputTopic, "cupcat", sched.toScheduleWithoutHeaders.toAvro)

          val published = consumeSomeFrom[Array[Byte]]("cupcat", 1).headOption

          published shouldBe defined
          val res = scheduleConsumerRecordDecoder(published.get)
          res.headers shouldBe Option(Map.empty)
        }
      }
    }

    trait TestContext {
      implicit class HeadersFromOps(val h: ScheduleReader.In) {
        def headers =
          h.fold(_ => none[Map[String, Array[Byte]]], {
            case (_, ose) => ose.fold(none[Map[String, Array[Byte]]])(_.headers.some)
          })
        def headerKeys   = headers.map(_.keys.toList)
        def headerValues = headers.map(_.values.toList.map(_.toList))
      }
    }
  }
}
