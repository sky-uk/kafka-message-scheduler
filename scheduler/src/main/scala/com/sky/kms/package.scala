package com.sky

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.MILLIS

import cats.data.Reader
import cats.syntax.either._
import cats.syntax.option._
import com.sksamuel.avro4s.{AvroInputStream, AvroSchema, Decoder}
import com.sky.kms.avro._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka.ConsumerRecordDecoder
import com.sky.kms.streams.ScheduleReader

import scala.concurrent.duration._
import scala.util.Try

package object kms {

  implicit val scheduleConsumerRecordDecoder: ConsumerRecordDecoder[ScheduleReader.In] =
    cr => Option(cr.value).fold[ScheduleReader.In]((cr.key, None).asRight) { bytes =>
      for {
        scheduleTry <- Either.fromOption(valueDecoder(bytes), InvalidSchemaError(cr.key))
        avroSchedule <- scheduleTry.toEither.leftMap(AvroMessageFormatError(cr.key, _))
        delay <- Either
          .catchNonFatal(MILLIS.between(OffsetDateTime.now, avroSchedule.time).millis)
          .leftMap(_ => InvalidTimeError(cr.key, avroSchedule.time))
      } yield cr.key -> ScheduleEvent(delay, cr.topic, avroSchedule.topic, avroSchedule.key, avroSchedule.value, avroSchedule.headers).some
    }

  implicit val scheduleDecoder = Decoder[Schedule]

  private val scheduleSchema = AvroSchema[Schedule]

  private def valueDecoder(avro: Array[Byte]): Option[Try[Schedule]] =
    AvroInputStream.binary[Schedule].from(avro).build(scheduleSchema).tryIterator.toSeq.headOption

  type Start[T] = Reader[SchedulerApp, T]

  object Start {
    def apply[T](f: SchedulerApp => T): Start[T] = Reader(f)
  }

}
