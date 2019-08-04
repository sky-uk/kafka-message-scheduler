package com.sky.kms.e2e

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import com.sky.kms._
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.domain.ScheduleEvent
import com.sky.kms.utils.TestDataUtils._
import net.manub.embeddedkafka.Codecs.{
  stringSerializer,
  nullDeserializer => arrayByteDeserializer,
  nullSerializer => arrayByteSerializer
}

class SchedulerSchemaEvolutionSpec extends SchedulerIntSpecBase with RandomDataGenerator {

  "scheduler schema" should {

    "be able to decoded new schedule events" in {
      withRunningKafka {
        withSchedulerApp {
          val scheduleWithHeaders = random[ScheduleEvent]
          val sched               = scheduleWithHeaders.copy(inputTopic = "cupcat").secondsFromNow(4)

          publishToKafka(sched.inputTopic, "cupcat", sched.toSchedule.toAvro)

          val published = consumeSomeFrom[Array[Byte]]("cupcat", 1).headOption
          published shouldBe defined
          scheduleConsumerRecordDecoder(published.get).isRight shouldBe true
        }
      }
    }

    "be able to decoded old schedule events" in {
      withRunningKafka {
        withSchedulerApp {
          val scheduleNoHeaders = random[ScheduleEventNoHeaders]
          val sched             = scheduleNoHeaders.copy(inputTopic = "cupcat").secondsFromNow(4)

          publishToKafka(sched.inputTopic, "cupcat", sched.toScheduleWithoutHeaders.toAvro)

          val published = consumeSomeFrom[Array[Byte]]("cupcat", 1).headOption

          published shouldBe defined
          scheduleConsumerRecordDecoder(published.get).isRight shouldBe true
        }
      }
    }

  }

}
