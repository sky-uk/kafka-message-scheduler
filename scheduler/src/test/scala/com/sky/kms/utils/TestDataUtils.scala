package com.sky.kms.utils

import java.io.ByteArrayOutputStream
import java.time.{Duration, OffsetDateTime, ZoneOffset, ZonedDateTime}

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.scaladsl.{Sink, Source}
import cats.Eval
import com.fortysevendeg.scalacheck.datetime.GenDateTime.genDateTimeWithinRange
import com.fortysevendeg.scalacheck.datetime.instances.jdk8._
import com.sksamuel.avro4s.{AvroOutputStream, Encoder, SchemaFor}
import com.sky.kms.SchedulerApp
import com.sky.kms.avro._
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain.Schedule.{ScheduleNoHeaders, ScheduleWithHeaders}
import com.sky.kms.domain.{Schedule, ScheduleEvent}
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import org.scalacheck.{Arbitrary, Gen}
import org.zalando.grafter.syntax.rewriter._

import scala.language.higherKinds
import scala.concurrent.Future
import scala.concurrent.duration._

object TestDataUtils {

  implicit val arbAlphaString: Arbitrary[String] =
    Arbitrary(Gen.alphaStr.suchThat(_.nonEmpty).retryUntil(_.nonEmpty))

  implicit val arbNextMonthOffsetDateTime: Arbitrary[OffsetDateTime] = {
    val from  = ZonedDateTime.now()
    val range = Duration.ofDays(20)
    Arbitrary(genDateTimeWithinRange(from, range).map(_.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime))
  }

  implicit val arbFiniteDuration: Arbitrary[FiniteDuration] =
    Arbitrary(arbNextMonthOffsetDateTime.arbitrary.map(_.getSecond.seconds))

  implicit val schemaForScheduleWithHeaders  = SchemaFor[ScheduleWithHeaders]
  implicit val schemaForScheduleNoHeaders    = SchemaFor[ScheduleNoHeaders]
  implicit val encoderForScheduleWithHeaders = Encoder[ScheduleWithHeaders]
  implicit val encoderForScheduleNoHeaders   = Encoder[ScheduleNoHeaders]

  implicit class ScheduleEventOps(val schedule: ScheduleEvent) extends AnyVal {
    def toSchedule: ScheduleWithHeaders = {
      val time = OffsetDateTime.now().toInstant.plusMillis(schedule.delay.toMillis).atOffset(ZoneOffset.UTC)
      ScheduleWithHeaders(time, schedule.outputTopic, schedule.key, schedule.value, schedule.headers)
    }

    def secondsFromNow(seconds: Long): ScheduleEvent =
      schedule.copy(delay = seconds.seconds)

    def toScheduledMessage: ScheduledMessage =
      ScheduledMessage(schedule.inputTopic, schedule.outputTopic, schedule.key, schedule.value, schedule.headers)

    def headerKeys   = schedule.headers.keys
    def headerValues = schedule.headers.values
  }

  implicit class ScheduleEventNoHeadersOps(val schedule: ScheduleEventNoHeaders) extends AnyVal {
    def toScheduleWithoutHeaders: ScheduleNoHeaders = {
      val time = OffsetDateTime.now().toInstant.plusMillis(schedule.delay.toMillis).atOffset(ZoneOffset.UTC)
      ScheduleNoHeaders(time, schedule.outputTopic, schedule.key, schedule.value)
    }

    def secondsFromNow(seconds: Long): ScheduleEventNoHeaders =
      schedule.copy(delay = seconds.seconds)

    def toScheduledMessage: ScheduledMessage =
      ScheduledMessage(schedule.inputTopic, schedule.outputTopic, schedule.key, schedule.value, Map.empty)
  }

  implicit class ScheduleOps[T <: Schedule](val schedule: T) extends AnyVal {
    def toAvro(implicit sf: SchemaFor[T], e: Encoder[T]): Array[Byte] = toAvroFrom(schedule)
    def timeInMillis: Long                                            = schedule.getTime.toInstant.toEpochMilli
  }

  private def toAvroFrom[T <: Schedule : Encoder : SchemaFor](t: T) = {
    val baos   = new ByteArrayOutputStream()
    val output = AvroOutputStream.binary[T].to(baos).build()
    output.write(t)
    output.close()
    baos.toByteArray
  }

  implicit class SchedulerAppOps(val schedulerApp: SchedulerApp) extends AnyVal {
    def withReaderSource(src: Source[ScheduleReader.In, (Future[Done], Future[Control])])(implicit
        as: ActorSystem
    ): SchedulerApp =
      schedulerApp.copy(reader = schedulerApp.reader.copy(scheduleSource = Eval.later(src)))

    def withPublisherSink(
        sink: Sink[ScheduledMessagePublisher.SinkIn, ScheduledMessagePublisher.SinkMat]
    ): SchedulerApp =
      schedulerApp.modifyWith[Any] { case pub: ScheduledMessagePublisher =>
        pub.replace(Eval.later(sink))
      }
  }

  case class ScheduleEventNoHeaders(
      delay: FiniteDuration,
      inputTopic: String,
      outputTopic: String,
      key: Array[Byte],
      value: Option[Array[Byte]]
  )

}
