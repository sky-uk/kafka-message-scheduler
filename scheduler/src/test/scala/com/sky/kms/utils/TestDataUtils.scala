package com.sky.kms.utils

import java.io.ByteArrayOutputStream
import java.time.{Duration, OffsetDateTime, ZoneOffset, ZonedDateTime}

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.scaladsl.{Sink, Source}
import cats.Eval
import com.fortysevendeg.scalacheck.datetime.GenDateTime.genDateTimeWithinRange
import com.fortysevendeg.scalacheck.datetime.instances.jdk8._
import com.sksamuel.avro4s.{AvroOutputStream, AvroSchema, Encoder}
import com.sky.kms.avro._
import com.sky.kms.BackoffRestartStrategy.Restarts
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain.{Schedule, ScheduleEvent}
import com.sky.kms.kafka.KafkaMessage
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import com.sky.kms.{BackoffRestartStrategy, SchedulerApp}
import eu.timepit.refined.auto._
import org.scalacheck.{Arbitrary, Gen}
import org.zalando.grafter.syntax.rewriter._

import scala.concurrent.duration._

object TestDataUtils {

  val NoRestarts = BackoffRestartStrategy(10.millis, 10.millis, Restarts(0))

  implicit val arbAlphaString: Arbitrary[String] =
    Arbitrary(Gen.alphaStr.suchThat(_.nonEmpty).retryUntil(_.nonEmpty))

  implicit val arbNextMonthOffsetDateTime: Arbitrary[OffsetDateTime] = {
    val from = ZonedDateTime.now()
    val range = Duration.ofDays(20)
    Arbitrary(genDateTimeWithinRange(from, range).map(_.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime))
  }

  private val scheduleSchema = AvroSchema[Schedule]

  implicit class ScheduleOps(val schedule: ScheduleEvent) extends AnyVal {
    def toAvro(implicit toRecord: Encoder[Schedule]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[Schedule].to(baos).build(scheduleSchema)
      output.write(Schedule(schedule.time, schedule.outputTopic, schedule.key, schedule.value))
      output.close()
      baos.toByteArray
    }

    def timeInMillis: Long = schedule.time.toInstant.toEpochMilli

    def secondsFromNow(seconds: Long): ScheduleEvent =
      schedule.copy(time = OffsetDateTime.now().plusSeconds(seconds))

    def toScheduledMessage: ScheduledMessage =
      ScheduledMessage(schedule.inputTopic, schedule.outputTopic, schedule.key, schedule.value)
  }

  implicit class SchedulerAppOps(val schedulerApp: SchedulerApp) extends AnyVal {
    def withReaderRestartStrategy(strategy: BackoffRestartStrategy)(implicit as: ActorSystem): SchedulerApp =
      schedulerApp.copy(reader = schedulerApp.reader.copy[KafkaMessage](restartStrategy = strategy))

    def withReaderSource(src: Source[KafkaMessage[ScheduleReader.In], Control])(implicit as: ActorSystem): SchedulerApp =
      schedulerApp.copy(reader = schedulerApp.reader.copy[KafkaMessage](loadProcessedSchedules = _ => Source.empty, scheduleSource = Eval.later(src)))

    def withPublisherSink(sink: Sink[ScheduledMessagePublisher.SinkIn, ScheduledMessagePublisher.SinkMat]): SchedulerApp =
      schedulerApp.modifyWith[Any] {
        case pub: ScheduledMessagePublisher => pub.replace(Eval.later(sink))
      }
  }

}
