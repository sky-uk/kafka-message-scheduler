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

  implicit class ScheduleEventOps(private val schedule: ScheduleEvent) extends AnyVal {
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

  implicit class ScheduleEventNoHeadersOps(private val schedule: ScheduleEventNoHeaders) extends AnyVal {
    def toScheduleWithoutHeaders: ScheduleNoHeaders = {
      val time = OffsetDateTime.now().toInstant.plusMillis(schedule.delay.toMillis).atOffset(ZoneOffset.UTC)
      ScheduleNoHeaders(time, schedule.outputTopic, schedule.key, schedule.value)
    }

    def secondsFromNow(seconds: Long): ScheduleEventNoHeaders =
      schedule.copy(delay = seconds.seconds)

    def toScheduledMessage: ScheduledMessage =
      ScheduledMessage(schedule.inputTopic, schedule.outputTopic, schedule.key, schedule.value, Map.empty)
  }

  implicit class ScheduleOps[T <: Schedule](private val schedule: T) extends AnyVal {
    def toAvro(implicit e: Encoder[T]): Array[Byte] = toAvroFrom(schedule)
    def timeInMillis: Long                          = schedule.getTime.toInstant.toEpochMilli
  }

  private def toAvroFrom[T <: Schedule : Encoder](t: T) = {
    val baos   = new ByteArrayOutputStream()
    val output = AvroOutputStream.binary[T].to(baos).build()
    output.write(t)
    output.close()
    baos.toByteArray
  }

  implicit class SchedulerAppOps(private val schedulerApp: SchedulerApp) extends AnyVal {
    def withReaderSource(src: Source[ScheduleReader.In, (Future[Done], Future[Control])])(implicit
        as: ActorSystem
    ): SchedulerApp =
      schedulerApp.copy(reader = schedulerApp.reader.copy(scheduleSource = Eval.later(src)))

    def withPublisherSink(
        newSink: Sink[ScheduledMessagePublisher.SinkIn, ScheduledMessagePublisher.SinkMat]
    ): SchedulerApp =
      schedulerApp.copy(publisher = schedulerApp.publisher.copy(publisherSink = Eval.later(newSink)))
  }

  case class ScheduleEventNoHeaders(
      delay: FiniteDuration,
      inputTopic: String,
      outputTopic: String,
      key: Array[Byte],
      value: Option[Array[Byte]]
  )

}
