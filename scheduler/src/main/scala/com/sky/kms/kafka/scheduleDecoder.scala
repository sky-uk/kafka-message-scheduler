package com.sky.kms.kafka

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.MILLIS

import cats.syntax.either._
import cats.syntax.option._
import com.sksamuel.avro4s.{AvroInputStream, AvroSchema, Decoder, SchemaFor}
import com.sky.kms.avro._
import com.sky.kms.domain.ApplicationError.{AvroMessageFormatError, InvalidSchemaError, InvalidTimeError}
import com.sky.kms.domain.Schedule.{ScheduleNoHeaders, ScheduleWithHeaders}
import com.sky.kms.domain.{ApplicationError, Schedule}
import com.sky.kms.streams.ScheduleReader
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.duration._
import scala.util.Try

sealed trait ScheduleDecoder extends Product with Serializable {
  def decode(cr: ConsumerRecord[String, Array[Byte]]): ScheduleReader.In
}

case object AvroBinary extends ScheduleDecoder {
  override def decode(cr: ConsumerRecord[String, Array[Byte]]): ScheduleReader.In =
    scheduleEvent(cr, decoder[ScheduleWithHeaders])
      .orElse(scheduleEvent(cr, decoder[ScheduleNoHeaders]))

  private def scheduleEvent[A <: Schedule](cr: ConsumerRecord[String, Array[Byte]],
                                           decode: Array[Byte] => Option[Try[A]]): ScheduleReader.In =
    Option(cr.value).fold[ScheduleReader.In]((cr.key, None).asRight[ApplicationError]) { bytes =>
      for {
        scheduleTry  <- Either.fromOption(decode(bytes), InvalidSchemaError(cr.key))
        avroSchedule <- scheduleTry.toEither.leftMap(AvroMessageFormatError(cr.key, _))
        delay <- Either
                  .catchNonFatal(MILLIS.between(OffsetDateTime.now, avroSchedule.getTime).millis)
                  .leftMap(_ => InvalidTimeError(cr.key, avroSchedule.getTime))
      } yield cr.key -> avroSchedule.toScheduleEvent(delay, cr.topic).some
    }

  private def decoder[T : Decoder : SchemaFor]: Array[Byte] => Option[Try[T]] =
    bytes => AvroInputStream.binary[T].from(bytes).build(AvroSchema[T]).tryIterator.toSeq.headOption
}
