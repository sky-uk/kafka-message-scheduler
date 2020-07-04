package com.sky.kms.unit

import com.sksamuel.avro4s.{AvroSchema, ToRecord}
import com.sky.kms.avro._
import com.sky.kms.base.SpecBase
import com.sky.kms.domain.ApplicationError.{AvroMessageFormatError, InvalidSchemaError}
import com.sky.kms.domain.Schedule
import com.sky.kms.domain.Schedule.ScheduleNoHeaders
import com.sky.kms.kafka.{AvroBinary, ConfluentWireFormat}
import com.sky.kms.unit.behaviour.ScheduleDecoderBehaviour
import com.sky.kms.utils.{ScheduleMatcher, TestDataUtils}
import io.confluent.kafka.schemaregistry.client.MockSchemaRegistryClient
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.errors.SerializationException

class ScheduleDecoderSpec extends SpecBase with ScheduleMatcher with ScheduleDecoderBehaviour {

  val ScheduleTopic = "scheduleTopic"
  val ScheduleId    = "scheduleId"

  "AvroBinary" should {
    behave like scheduleDecoder(AvroBinary.decode, TestDataUtils.toBinaryAvroFrom, TestDataUtils.toBinaryAvroFrom)

    "error if message does not adhere to our schema" in {
      val cr = new ConsumerRecord[String, Array[Byte]](ScheduleTopic, 1, 1L, ScheduleId, Array.emptyByteArray)

      AvroBinary.decode(cr) shouldBe Left(InvalidSchemaError(ScheduleId))
    }
  }

  "ConfluentWireFormat" should {
    val client = new MockSchemaRegistryClient
    client.register(s"$ScheduleTopic-value", AvroSchema[Schedule])
    client.register(s"$ScheduleTopic-value", AvroSchema[ScheduleNoHeaders])

    val deserializer = new KafkaAvroDeserializer(client)
    val serializer   = new KafkaAvroSerializer(client)

    val serialize = (schedule: Schedule) => serializer.serialize(ScheduleTopic, ToRecord[Schedule].to(schedule))
    val legacySerialize =
      (schedule: ScheduleNoHeaders) => serializer.serialize(ScheduleTopic, ToRecord[ScheduleNoHeaders].to(schedule))

    behave like scheduleDecoder(ConfluentWireFormat.decode(deserializer), serialize, legacySerialize)

    "error if message is not in the confluent wire format" in {
      val cr = new ConsumerRecord[String, Array[Byte]](ScheduleTopic, 1, 1L, ScheduleId, Array.emptyByteArray)

      ConfluentWireFormat.decode(deserializer)(cr) should matchPattern {
        case Left(AvroMessageFormatError(ScheduleId, _: SerializationException)) =>
      }
    }
  }
}
