package com.sky

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit.MILLIS

import cats.data.{Nested, Reader}
import cats.implicits._
import com.sksamuel.avro4s.{AvroInputStream, AvroSchema, Decoder}
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
      Nested(buildSchedule(cr, valueDecoder)(_.time)).map {
        case (dur, sched) => toScheduleEvent(dur, cr.topic, sched)
      }.value.leftFlatMap { _ =>
        Nested(buildSchedule(cr, valueNoHeadersDecoder)(_.time)).map {
          case (dur, sched) => toScheduleEvent(dur, cr.topic, sched)
        }.value
      }.map(cr.key -> _)

  private def buildSchedule[A, B](cr: ConsumerRecord[String, A], f: A => Option[Try[B]])(
      g: B => OffsetDateTime): Either[ApplicationError, Option[(FiniteDuration, B)]] =
    Option(cr.value).fold(none[(FiniteDuration, B)].asRight[ApplicationError]) { bytes =>
      for {
        scheduleTry  <- Either.fromOption(f(bytes), InvalidSchemaError(cr.key))
        avroSchedule <- scheduleTry.toEither.leftMap(AvroMessageFormatError(cr.key, _))
        delay <- Either
                  .catchNonFatal(MILLIS.between(OffsetDateTime.now, g(avroSchedule)).millis)
                  .leftMap(_ => InvalidTimeError(cr.key, g(avroSchedule)))
      } yield (delay, avroSchedule).some
    }

  implicit val scheduleDecoder = Decoder[ScheduleWithHeaders]

  private val scheduleSchema = AvroSchema[ScheduleWithHeaders]

  private val scheduleNoHeadersSchema = AvroSchema[ScheduleNoHeaders]

  private val valueDecoder: Array[Byte] => Option[Try[ScheduleWithHeaders]] = avro =>
    AvroInputStream.binary[ScheduleWithHeaders].from(avro).build(scheduleSchema).tryIterator.toSeq.headOption

  private val valueNoHeadersDecoder: Array[Byte] => Option[Try[ScheduleNoHeaders]] = avro =>
    AvroInputStream.binary[ScheduleNoHeaders].from(avro).build(scheduleNoHeadersSchema).tryIterator.toSeq.headOption

  private def toScheduleEvent(dur: FiniteDuration, topic: String, sched: ScheduleNoHeaders) =
    ScheduleEvent(dur, topic, sched.topic, sched.key, sched.value, Map.empty)

  private def toScheduleEvent(dur: FiniteDuration, topic: String, sched: ScheduleWithHeaders) =
    ScheduleEvent(dur, topic, sched.topic, sched.key, sched.value, Map.empty)

  type Start[T] = Reader[SchedulerApp, T]

  object Start {
    def apply[T](f: SchedulerApp => T): Start[T] = Reader(f)
  }

}
