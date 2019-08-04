package com.sky

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.MILLIS

import cats.data.{Nested, Reader}
import cats.implicits._
import com.sksamuel.avro4s.{AvroInputStream, AvroSchema, Decoder, SchemaFor}
import com.sky.kms.avro._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka.ConsumerRecordDecoder
import com.sky.kms.streams.ScheduleReader
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.duration._
import scala.util.Try

package object kms {

  implicit val scheduleConsumerRecordDecoder: ConsumerRecordDecoder[ScheduleReader.In] =
    (cr: ConsumerRecord[String, Array[Byte]]) =>
      scheduleEvent(cr, decoder[ScheduleWithHeaders])(_.time, toScheduleEvent(cr.topic, _, _)).leftFlatMap { _ =>
        scheduleEvent(cr, decoder[ScheduleNoHeaders])(_.time, toScheduleEvent(cr.topic, _, _))
      }.map(cr.key -> _)

  private def scheduleEvent[A, B](cr: ConsumerRecord[String, Array[Byte]], decode: Array[Byte] => Option[Try[B]])(
      scheduleDate: B => OffsetDateTime,
      scheduleEventFrom: (FiniteDuration, B) => ScheduleEvent): Either[ApplicationError, Option[ScheduleEvent]] =
    Option(cr.value).fold(none[ScheduleEvent].asRight[ApplicationError]) { bytes =>
      Nested(for {
        scheduleTry  <- Either.fromOption(decode(bytes), InvalidSchemaError(cr.key))
        avroSchedule <- scheduleTry.toEither.leftMap(AvroMessageFormatError(cr.key, _))
        delay <- Either
                  .catchNonFatal(MILLIS.between(OffsetDateTime.now, scheduleDate(avroSchedule)).millis)
                  .leftMap(_ => InvalidTimeError(cr.key, scheduleDate(avroSchedule)))
      } yield (delay, avroSchedule).some).map { case (dur, t) => scheduleEventFrom(dur, t) }.value
    }

  implicit val scheduleDecoder = Decoder[ScheduleWithHeaders]

  implicit val scheduleSchema = AvroSchema[ScheduleWithHeaders]

  implicit val scheduleNoHeadersSchema = AvroSchema[ScheduleNoHeaders]

  private def decoder[T : Decoder : SchemaFor]: Array[Byte] => Option[Try[T]] = { bytes =>
    AvroInputStream.binary[T].from(bytes).build(implicitly[SchemaFor[T]].schema).tryIterator.toSeq.headOption
  }

  private def toScheduleEvent(topic: String, dur: FiniteDuration, sched: ScheduleNoHeaders) =
    ScheduleEvent(dur, topic, sched.topic, sched.key, sched.value, Map.empty)

  private def toScheduleEvent(topic: String, dur: FiniteDuration, sched: ScheduleWithHeaders) =
    ScheduleEvent(dur, topic, sched.topic, sched.key, sched.value, sched.headers)

  type Start[T] = Reader[SchedulerApp, T]

  object Start {
    def apply[T](f: SchedulerApp => T): Start[T] = Reader(f)
  }

}
