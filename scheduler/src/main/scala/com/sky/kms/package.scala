package com.sky

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.MILLIS

import cats.data.Reader
import cats.syntax.all.*
import com.sksamuel.avro4s.{AvroInputStream, AvroSchema, Decoder, SchemaFor}
import com.sky.kms.avro.*
import com.sky.kms.domain.ApplicationError.*
import com.sky.kms.domain.Schedule.{ScheduleNoHeaders, ScheduleWithHeaders}
import com.sky.kms.domain.*
import com.sky.kms.kafka.ConsumerRecordDecoder
import com.sky.kms.streams.ScheduleReader
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.duration.*
import scala.util.Try

package object kms {

  implicit val scheduleConsumerRecordDecoder: ConsumerRecordDecoder[ScheduleReader.In] =
    (cr: ConsumerRecord[String, Array[Byte]]) =>
      scheduleEvent(cr, decoder[ScheduleWithHeaders])
        .orElse(scheduleEvent(cr, decoder[ScheduleNoHeaders]))

  private def scheduleEvent[A <: Schedule](
      cr: ConsumerRecord[String, Array[Byte]],
      decode: Array[Byte] => Option[Try[A]]
  ): ScheduleReader.In =
    Option(cr.value).fold[ScheduleReader.In]((cr.key, None).asRight[ApplicationError]) { bytes =>
      for {
        scheduleTry  <- Either.fromOption(decode(bytes), InvalidSchemaError(cr.key))
        avroSchedule <- scheduleTry.toEither.leftMap(AvroMessageFormatError(cr.key, _))
        delay        <- Either
                          .catchNonFatal(MILLIS.between(OffsetDateTime.now, avroSchedule.getTime).millis)
                          .leftMap(_ => InvalidTimeError(cr.key, avroSchedule.getTime))
      } yield cr.key -> avroSchedule.toScheduleEvent(delay, cr.topic).some
    }

  private def decoder[T : Decoder : SchemaFor]: Array[Byte] => Option[Try[T]] =
    bytes => AvroInputStream.binary[T].from(bytes).build(AvroSchema[T]).tryIterator.toSeq.headOption

  type Start[T] = Reader[SchedulerApp, T]

  object Start {
    def apply[T](f: SchedulerApp => T): Start[T] = Reader(f)
  }

}
