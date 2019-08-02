package com.sky.kms.e2e

import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime

import com.sksamuel.avro4s.{AvroInputStream, AvroOutputStream, AvroSchema, Encoder, SchemaFor}
import com.sky.kms.base.SchedulerIntSpecBase
import com.sky.kms.domain.ScheduleId
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, Deserializer, StringDeserializer}

import scala.util.Success

trait EvolvingData
case class OldData(name: String)                                            extends EvolvingData
case class NewData(name: String, newField: Map[String, String] = Map.empty) extends EvolvingData

class SchedulerSchemaEvolutionSpec extends SchedulerIntSpecBase {

  "scheduler schema" should {
    "be able to decoded old schema against a new case class structure" in new TestContext {
      withRunningKafka {
        withSchedulerApp {

          val oldData = OldData("cupcat")
          val newData = NewData("cupcat", Map("cup" -> "cat"))

          publish(List(("cupcat", oldData)))
          publish(List(("cupcat2", newData)))

          val (old :: evolved :: Nil) = consumeSomeFrom[Array[Byte]]("cupcat", 2)

          println(s"evolved: $evolved")
          val t =
            AvroInputStream
              .binary[NewData]
              .from(old.value())
              .build(oldScheduleSchema)
              .tryIterator
              .toSeq
              .headOption

          val u =
            AvroInputStream
              .binary[NewData]
              .from(evolved.value())
              .build(newScheduleSchema)
              .tryIterator
              .toSeq
              .headOption

          t shouldBe Some(Success(NewData("cupcat", Map.empty)))
          u shouldBe Some(Success(NewData("cupcat", Map("cup" -> "cat"))))

        }
      }
    }

  }

  trait TestContext {

    implicit val oldScheduleSchema  = AvroSchema[OldData]
    implicit val newScheduleSchema  = AvroSchema[NewData]
    implicit val oldScheduleEncoder = Encoder[OldData]

    implicit val ss = net.manub.embeddedkafka.Codecs.stringSerializer
    implicit val ns = net.manub.embeddedkafka.Codecs.nullSerializer

    implicit val sd = net.manub.embeddedkafka.Codecs.stringDeserializer
    implicit val nd = net.manub.embeddedkafka.Codecs.nullDeserializer

    def publish[T](l: List[(ScheduleId, T)])(implicit sf: SchemaFor[T], e: Encoder[T]): List[OffsetDateTime] = l.map {
      case (id, scheduleEvent) =>
        publishToKafka("cupcat", id, toAvro[T](scheduleEvent))
        OffsetDateTime.now()
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
