package com.sky.kms.utils

import java.io.ByteArrayOutputStream
import java.time.{Duration, OffsetDateTime, ZoneOffset, ZonedDateTime}

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.scaladsl.{Flow, Sink, Source}
import cats.Eval
import com.fortysevendeg.scalacheck.datetime.GenDateTime.genDateTimeWithinRange
import com.fortysevendeg.scalacheck.datetime.instances.jdk8._
import com.sksamuel.avro4s.{AvroOutputStream, AvroSchema, Encoder}
import com.sky.kms.avro._
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain.{ApplicationError, Schedule, ScheduleEvent}
import com.sky.kms.kafka.KafkaMessage
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import com.sky.kms.SchedulerApp
import com.sky.kms.actors.SchedulingActor.Ack
import com.sky.map.commons.akka.streams.BackoffRestartStrategy
import com.sky.map.commons.akka.streams.BackoffRestartStrategy.Restarts
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

  implicit val arbFiniteDuration: Arbitrary[FiniteDuration] =
    Arbitrary(arbNextMonthOffsetDateTime.arbitrary.map(_.getSecond.seconds))

  implicit val scheduleEncoder = Encoder[Schedule]

  private val scheduleSchema = AvroSchema[Schedule]

  implicit class ScheduleEventOps(val schedule: ScheduleEvent) extends AnyVal {
    def toSchedule: Schedule = {
      val time = OffsetDateTime.now().toInstant.plusMillis(schedule.delay.toMillis).atOffset(ZoneOffset.UTC)
      Schedule(time, schedule.outputTopic, schedule.key, schedule.value)
    }

    def secondsFromNow(secondsAsLong: Long): ScheduleEvent =
      schedule.copy(delay = secondsAsLong.seconds)

    def toScheduledMessage: ScheduledMessage =
      ScheduledMessage(schedule.inputTopic, schedule.outputTopic, schedule.key, schedule.value)
  }

  implicit class ScheduleOps(val schedule: Schedule) extends AnyVal {
    def toAvro: Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[Schedule].to(baos).build(scheduleSchema)
      output.write(schedule)
      output.close()
      baos.toByteArray
    }

    def timeInMillis: Long = schedule.time.toInstant.toEpochMilli
  }

  implicit class SchedulerAppOps(val schedulerApp: SchedulerApp) extends AnyVal {
    def withReaderRestartStrategy(strategy: BackoffRestartStrategy)(implicit as: ActorSystem): SchedulerApp =
      schedulerApp.copy(reader = schedulerApp.reader.copy[KafkaMessage](restartStrategy = strategy))

    def withReaderSource(src: Source[KafkaMessage[ScheduleReader.In], Control])(implicit as: ActorSystem): SchedulerApp =
      schedulerApp.copy(reader = schedulerApp.reader.copy[KafkaMessage](
        loadProcessedSchedules = _ => Source.empty,
        scheduleSource = Eval.later(src),
        commit = Flow[KafkaMessage[Either[ApplicationError, Ack.type]]].map(_ => Done)))

    def withPublisherSink(sink: Sink[ScheduledMessagePublisher.SinkIn, ScheduledMessagePublisher.SinkMat]): SchedulerApp =
      schedulerApp.modifyWith[Any] {
        case pub: ScheduledMessagePublisher => pub.replace(Eval.later(sink))
      }
  }

}
