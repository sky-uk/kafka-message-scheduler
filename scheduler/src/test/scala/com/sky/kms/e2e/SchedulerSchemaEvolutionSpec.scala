package com.sky.kms.e2e

import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import com.sksamuel.avro4s.{AvroOutputStream, Encoder, SchemaFor}
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.domain.{ScheduleEvent, ScheduleEventNoHeaders, ScheduleId}
import com.sky.kms.utils.TestDataUtils._
import net.manub.embeddedkafka.Codecs.{stringSerializer, nullSerializer => arrayByteSerializer}
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, Deserializer, StringDeserializer}
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import com.sky.kms._

class SchedulerSchemaEvolutionSpec extends SchedulerIntSpecBase with ScalaCheckPropertyChecks with RandomDataGenerator {

  implicit val propertyCheckConf = PropertyCheckConfiguration(minSuccessful = 100, sizeRange = 30)

  "scheduler schema" should {

    "be able to decoded new schedule events" in new TestContext {
      withRunningKafka {
        withSchedulerApp {
          val scheduleWithHeaders = random[ScheduleEvent]
          val sched               = scheduleWithHeaders.copy(inputTopic = "cupcat").secondsFromNow(4)
          println(sched)

          publish(List(("cupcat", sched)))

          val published = consumeSomeFrom[Array[Byte]]("cupcat", 1)

          val decoded = scheduleConsumerRecordDecoder(published.head)

          decoded.isRight shouldBe true
        }
      }
    }

    "be able to decoded old schedule events" in new TestContext {
      withRunningKafka {
        withSchedulerApp {
          val scheduleNoHeaders = random[ScheduleEventNoHeaders]
          val sched             = scheduleNoHeaders.copy(inputTopic = "cupcat").secondsFromNow(4)

          publishNoHeaders(List(("cupcat", sched)))

          val publishedNoHeaders = consumeSomeFrom[Array[Byte]]("cupcat", 1)

          val decoded = scheduleConsumerRecordDecoder(publishedNoHeaders.head)

          decoded.right shouldBe Option(scheduleNoHeaders)
        }
      }
    }

  }

  trait TestContext {

    def publish: List[(ScheduleId, ScheduleEvent)] => List[OffsetDateTime] = _.map {
      case (id, scheduleEvent) =>
        val schedule = scheduleEvent.toSchedule
        publishToKafka(scheduleEvent.inputTopic, id, schedule.toAvro)
        schedule.time
    }

    def publishNoHeaders: List[(ScheduleId, ScheduleEventNoHeaders)] => List[OffsetDateTime] = _.map {
      case (id, scheduleEvent) =>
        val schedule = scheduleEvent.toScheduleWithoutHeaders
        publishToKafka(scheduleEvent.inputTopic, id, schedule.toAvro)
        schedule.time
    }

    implicit val keyDeserializer: Deserializer[String]        = new StringDeserializer()
    implicit val valueDeserializer: Deserializer[Array[Byte]] = new ByteArrayDeserializer()

    def toAvro[T](s: T)(implicit sf: SchemaFor[T], e: Encoder[T]): Array[Byte] = {
      val baos   = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[T].to(baos).build(sf.schema)
      output.write(s)
      output.close()
      baos.toByteArray
    }

  }

}
