package com.sky.kms.common

import java.io.ByteArrayOutputStream
import java.time._

import akka.stream.scaladsl.{Sink, Source}
import cats.Eval
import com.fortysevendeg.scalacheck.datetime.GenDateTime.genDateTimeWithinRange
import com.fortysevendeg.scalacheck.datetime.instances.jdk8._
import com.sksamuel.avro4s.{AvroOutputStream, ToRecord}
import com.sky.kms.SchedulerApp
import com.sky.kms.avro._
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain._
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import org.zalando.grafter.syntax.rewriter._
import org.scalacheck._

object TestDataUtils {

  implicit val arbAlphaString: Arbitrary[String] =
    Arbitrary(Gen.alphaStr.suchThat(_.nonEmpty).retryUntil(_.nonEmpty))

  implicit val arbNextMonthOffsetDateTime: Arbitrary[OffsetDateTime] = {
    val from = ZonedDateTime.now()
    val range = Duration.ofDays(20)
    Arbitrary(genDateTimeWithinRange(from, range).map(_.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime))
  }

  implicit class ScheduleOps(val schedule: Schedule) extends AnyVal {
    def toAvro(implicit toRecord: ToRecord[Schedule]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[Schedule](baos)
      output.write(schedule)
      output.close()
      baos.toByteArray
    }

    def timeInMillis: Long = schedule.time.toInstant.toEpochMilli

    def secondsFromNow(seconds: Long): Schedule =
      schedule.copy(time = OffsetDateTime.now().plusSeconds(seconds))

    def toScheduledMessage: ScheduledMessage =
      ScheduledMessage(schedule.topic, schedule.key, schedule.value)
  }

  implicit class SchedulerAppOps(val schedulerApp: SchedulerApp) extends AnyVal {
    def withReaderSource(src: Source[ScheduleReader.In, ScheduleReader.Mat]): SchedulerApp =
      schedulerApp.modifyWith[Any] {
        case reader: ScheduleReader => reader.replace(Eval.later(src))
      }

    def withPublisherSink(sink: Sink[ScheduledMessagePublisher.SinkIn, ScheduledMessagePublisher.SinkMat]): SchedulerApp =
      schedulerApp.modifyWith[Any] {
        case pub: ScheduledMessagePublisher => pub.replace(Eval.later(sink))
      }
  }

}
