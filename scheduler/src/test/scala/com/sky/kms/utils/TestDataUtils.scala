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
import com.sksamuel.avro4s.{AvroOutputStream, AvroSchema, Encoder}
import com.sky.kms.SchedulerApp
import com.sky.kms.avro._
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain.{Schedule, ScheduleEvent, ScheduleEventNoHeaders, ScheduleNoHeaders}
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import org.scalacheck.{Arbitrary, Gen}
import org.zalando.grafter.syntax.rewriter._

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

  implicit val scheduleEncoder = Encoder[Schedule]

  private val scheduleSchema = AvroSchema[Schedule]

  private val scheduleNoHeadersSchema = AvroSchema[ScheduleNoHeaders]

  implicit class ScheduleEventOps(val schedule: ScheduleEvent) extends AnyVal {
    def toSchedule: Schedule = {
      val time = OffsetDateTime.now().toInstant.plusMillis(schedule.delay.toMillis).atOffset(ZoneOffset.UTC)
      Schedule(time, schedule.outputTopic, schedule.key, schedule.value, schedule.headers)
    }

    def secondsFromNow(secondsAsLong: Long): ScheduleEvent =
      schedule.copy(delay = secondsAsLong.seconds)

    def toScheduledMessage: ScheduledMessage =
      ScheduledMessage(schedule.inputTopic, schedule.outputTopic, schedule.key, schedule.value, schedule.headers)
  }

  implicit class ScheduleEventNoHeadersOps(val schedule: ScheduleEventNoHeaders) extends AnyVal {
    def toScheduleWithoutHeaders: ScheduleNoHeaders = {
      val time = OffsetDateTime.now().toInstant.plusMillis(schedule.delay.toMillis).atOffset(ZoneOffset.UTC)
      ScheduleNoHeaders(time, schedule.outputTopic, schedule.key, schedule.value)
    }

    def secondsFromNow(secondsAsLong: Long): ScheduleEventNoHeaders =
      schedule.copy(delay = secondsAsLong.seconds)

    def toScheduledMessage: ScheduledMessage =
      ScheduledMessage(schedule.inputTopic, schedule.outputTopic, schedule.key, schedule.value, Map.empty)
  }

  implicit class ScheduleOps(val schedule: Schedule) extends AnyVal {
    def toAvro: Array[Byte] = {
      val baos   = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[Schedule].to(baos).build(scheduleSchema)
      output.write(schedule)
      output.close()
      baos.toByteArray
    }

    def timeInMillis: Long = schedule.time.toInstant.toEpochMilli
  }

  implicit class ScheduleNoHeadersOps(val schedule: ScheduleNoHeaders) extends AnyVal {
    def toAvro: Array[Byte] = {
      val baos   = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[ScheduleNoHeaders].to(baos).build(scheduleNoHeadersSchema)
      output.write(schedule)
      output.close()
      baos.toByteArray
    }

    def timeInMillis: Long = schedule.time.toInstant.toEpochMilli
  }

  implicit class SchedulerAppOps(val schedulerApp: SchedulerApp) extends AnyVal {
    def withReaderSource(src: Source[ScheduleReader.In, (Future[Done], Future[Control])])(
        implicit as: ActorSystem): SchedulerApp =
      schedulerApp.copy(reader = schedulerApp.reader.copy(scheduleSource = Eval.later(src)))

    def withPublisherSink(
        sink: Sink[ScheduledMessagePublisher.SinkIn, ScheduledMessagePublisher.SinkMat]): SchedulerApp =
      schedulerApp.modifyWith[Any] {
        case pub: ScheduledMessagePublisher => pub.replace(Eval.later(sink))
      }
  }

}
