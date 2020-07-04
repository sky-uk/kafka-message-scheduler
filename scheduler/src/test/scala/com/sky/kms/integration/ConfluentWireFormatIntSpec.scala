package com.sky.kms.integration

import com.sksamuel.avro4s.{AvroSchema, ToRecord}
import com.sky.kms.base.{KafkaIntSpecBase, SpecBase}
import com.sky.kms.domain.ApplicationError.AvroMessageFormatError
import com.sky.kms.domain.{Schedule, ScheduleEvent}
import com.sky.kms.kafka.ConfluentWireFormat
import com.sky.kms.streams.ScheduleReader.In
import com.sky.kms.utils.ScheduleMatcher
import com.sky.kms.utils.TestDataUtils._
import eu.timepit.refined.refineV
import eu.timepit.refined.string.Url
import io.confluent.kafka.serializers.KafkaAvroSerializer
import net.manub.embeddedkafka.Codecs.{
  stringSerializer,
  nullDeserializer => arrayByteDeserializer,
  nullSerializer => arrayByteSerializer
}
import org.apache.kafka.common.errors.SerializationException

class ConfluentWireFormatIntSpec extends KafkaIntSpecBase with SpecBase with ScheduleMatcher {

  "ConfluentWireFormat decoder" should {

    "be able to decode registered schedule events" in new TestContext {
      withRunningKafka {
        registryClient.register(s"$scheduleTopic-value", AvroSchema[Schedule])
        val scheduleBytes = serializer.serialize(scheduleTopic.value, ToRecord[Schedule].to(schedule.toSchedule))

        val (id, event) = publishAndGetDecoded(schedule.inputTopic, scheduleBytes)(decoder).value.right.value

        id shouldBe scheduleTopic.value
        event.value should matchScheduleEvent(schedule)
      }
    }

    "return an error if schema has not been registered" in new TestContext {
      withRunningKafka {
        val scheduleBytes = serializer.serialize(scheduleTopic.value, ToRecord[Schedule].to(schedule.toSchedule))

        val res = publishAndGetDecoded(schedule.inputTopic, scheduleBytes)(decoder).value

        res should matchPattern {
          case Left(AvroMessageFormatError(scheduleTopic.value, _: SerializationException)) =>
        }
      }
    }
  }

  trait TestContext {

    val decoder = ConfluentWireFormat(refineV[Url](registryUrl).getOrElse(fail()))

    val serializer = new KafkaAvroSerializer(registryClient)
    val schedule   = random[ScheduleEvent].copy(inputTopic = scheduleTopic.value)

    def publishAndGetDecoded(inputTopic: String, schedule: Array[Byte])(decoder: ConfluentWireFormat): Option[In] = {
      publishToKafka(inputTopic, inputTopic, schedule)
      consumeSomeFrom[Array[Byte]](inputTopic, 1).headOption.map(decoder.decode)
    }
  }

}
