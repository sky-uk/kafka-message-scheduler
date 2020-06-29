package com.sky.kms.integration

import cats.syntax.option._
import com.sksamuel.avro4s.{AvroSchema, ToRecord}
import com.sky.kms.base.{KafkaIntSpecBase, SpecBase}
import com.sky.kms.domain.Schedule.ScheduleNoHeaders
import com.sky.kms.domain.ScheduleEvent
import com.sky.kms.kafka.{AvroBinary, ConfluentWireFormat, ScheduleDecoder}
import com.sky.kms.streams.ScheduleReader
import com.sky.kms.streams.ScheduleReader.In
import com.sky.kms.utils.TestDataUtils._
import eu.timepit.refined.refineV
import eu.timepit.refined.string.Url
import io.confluent.kafka.serializers.KafkaAvroSerializer
import net.manub.embeddedkafka.Codecs.{
  stringSerializer,
  nullDeserializer => arrayByteDeserializer,
  nullSerializer => arrayByteSerializer
}

class ScheduleDecoderIntSpec extends KafkaIntSpecBase with SpecBase {

  "AvroBinary decoder" should {

    "be able to decode new schedule events" in new TestContext {
      withRunningKafka {
        val scheduleWithHeaders = random[ScheduleEvent]
        val schedule            = scheduleWithHeaders.copy(inputTopic = scheduleTopic).secondsFromNow(delay)

        val res = publishAndGetDecoded(schedule.inputTopic, schedule.toSchedule.toBinaryAvro)(AvroBinary)

        res.headerKeys should contain theSameElementsAs scheduleWithHeaders.headerKeys
        res.headerValues should contain theSameElementsAs scheduleWithHeaders.headerValues
      }
    }

    "be able to decode old schedule events" in new TestContext {
      withRunningKafka {
        val scheduleNoHeaders = random[ScheduleEventNoHeaders]
        val schedule          = scheduleNoHeaders.copy(inputTopic = scheduleTopic).secondsFromNow(delay)

        val res =
          publishAndGetDecoded(schedule.inputTopic, toBinaryAvroFrom(schedule.toScheduleWithoutHeaders))(AvroBinary)

        res.headers shouldBe Option(Map.empty)
      }
    }
  }

  "ConfluentWireFormat decoder" should {

    "be able to decode old schedule events" in new TestContext {
      withRunningKafka {
        val decoder = ConfluentWireFormat(refineV[Url](registryUrl).getOrElse(fail()))

        val serializer = new KafkaAvroSerializer(registryClient)
        registryClient.register(s"$scheduleTopic-value", AvroSchema[ScheduleNoHeaders])

        val scheduleNoHeaders = random[ScheduleEventNoHeaders]
        val schedule          = scheduleNoHeaders.copy(inputTopic = scheduleTopic).secondsFromNow(delay)
        val scheduleBytes =
          serializer.serialize(scheduleTopic, ToRecord[ScheduleNoHeaders].to(schedule.toScheduleWithoutHeaders))

        val res = publishAndGetDecoded(schedule.inputTopic, scheduleBytes)(decoder)

        res.headers shouldBe Option(Map.empty)
      }
    }
  }

  trait TestContext {

    val scheduleTopic = "cupcat"
    val delay         = 4

    def publishAndGetDecoded(inputTopic: String, schedule: Array[Byte])(decoder: ScheduleDecoder): Option[In] = {
      publishToKafka(inputTopic, inputTopic, schedule)
      consumeSomeFrom[Array[Byte]](inputTopic, 1).headOption.map(decoder.decode)
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
