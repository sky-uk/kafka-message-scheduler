package com.sky.kms.kafka

import cats.Functor
import cats.instances.option._
import cats.instances.try_._
import cats.syntax.either._
import cats.syntax.option._
import com.sksamuel.avro4s.{AvroInputStream, AvroSchema, Decoder, FromRecord, SchemaFor}
import com.sky.kms.avro._
import com.sky.kms.domain.ApplicationError.{AvroMessageFormatError, InvalidSchemaError}
import com.sky.kms.domain.Schedule.ScheduleNoHeaders
import com.sky.kms.domain.Schedule
import com.sky.kms.streams.ScheduleReader
import com.sky.kms.streams.ScheduleReader.In
import eu.timepit.refined.api.Refined
import eu.timepit.refined.string.Url
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.util.{Success, Try}

sealed trait ScheduleDecoder extends Product with Serializable {
  def decode(cr: ConsumerRecord[String, Array[Byte]]): ScheduleReader.In
}

case object AvroBinary extends ScheduleDecoder {
  override def decode(cr: ConsumerRecord[String, Array[Byte]]): ScheduleReader.In =
    Option(cr.value).fold[In]((cr.key, None).asRight) { bytes =>
      for {
        scheduleTry   <- Either.fromOption(decode(bytes), InvalidSchemaError(cr.key))
        avroSchedule  <- scheduleTry.toEither.leftMap(AvroMessageFormatError(cr.key, _))
        scheduleEvent <- avroSchedule.toScheduleEvent(cr.key, cr.topic)
      } yield cr.key -> scheduleEvent.some
    }

  private val F = Functor[Option] compose Functor[Try]

  private val decode: Array[Byte] => Option[Try[Schedule]] =
    bytes =>
      decoder[Schedule](bytes) match {
        case v @ Some(Success(_)) => v
        case _                    => F.map(decoder[ScheduleNoHeaders](bytes))(_.toSchedule)
    }

  private def decoder[T : Decoder : SchemaFor](bytes: Array[Byte]): Option[Try[T]] =
    AvroInputStream.binary[T].from(bytes).build(AvroSchema[T]).tryIterator.toSeq.headOption

}

final case class ConfluentWireFormat(schemaRegistryUrl: String Refined Url) extends ScheduleDecoder {

  private val client = new CachedSchemaRegistryClient(schemaRegistryUrl.value, 100)

  private val deserializer = new KafkaAvroDeserializer(client)

  override def decode(cr: ConsumerRecord[String, Array[Byte]]): In = ConfluentWireFormat.decode(deserializer)(cr)
}

object ConfluentWireFormat {
  def decode(deserializer: KafkaAvroDeserializer): ConsumerRecord[String, Array[Byte]] => In =
    cr =>
      Option(cr.value).fold[In]((cr.key, None).asRight) { bytes =>
        val scheduleAttempt = Either.catchNonFatal {
          FromRecord[Schedule].from(deserializer.deserialize(cr.topic, bytes).asInstanceOf[GenericRecord])
        }
        for {
          avroSchedule  <- scheduleAttempt.leftMap(AvroMessageFormatError(cr.key, _))
          scheduleEvent <- avroSchedule.toScheduleEvent(cr.key, cr.topic)
        } yield cr.key -> scheduleEvent.some
    }
}
