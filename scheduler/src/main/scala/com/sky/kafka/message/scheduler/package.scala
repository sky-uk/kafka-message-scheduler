package com.sky.kafka.message

import cats.data.Reader
import cats.syntax.either._
import cats.syntax.show._
import com.sksamuel.avro4s.AvroInputStream
import com.sky.kafka.message.scheduler.domain.ApplicationError._
import com.sky.kafka.message.scheduler.domain.{ApplicationError, _}
import com.sky.kafka.message.scheduler.kafka.{ConsumerRecordDecoder, ProducerRecordEncoder}
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import com.sky.kafka.message.scheduler.avro._

import scala.concurrent.duration.Duration
import scala.util.Try

package object scheduler extends LazyLogging {

  case class AppConfig(scheduler: SchedulerConfig)

  case class SchedulerConfig(scheduleTopic: String, shutdownTimeout: ShutdownTimeout, queueBufferSize: Int)

  object SchedulerConfig {
    def reader: Reader[AppConfig, SchedulerConfig] = Reader(_.scheduler)
  }

  case class ShutdownTimeout(stream: Duration, system: Duration)

  type DecodeScheduleResult = Either[ApplicationError, (ScheduleId, Option[Schedule])]

  implicit val scheduleConsumerRecordDecoder = new ConsumerRecordDecoder[DecodeScheduleResult] {
    def apply(cr: ConsumerRecord[String, Array[Byte]]): DecodeScheduleResult =
      consumerRecordDecoder(cr).leftMap { error =>
        logger.warn(error.show)
        error
      }
  }

  implicit val scheduleProducerRecordEncoder = new ProducerRecordEncoder[Schedule] {
    def apply(schedule: Schedule) = new ProducerRecord(schedule.topic, schedule.key, schedule.value)
  }

  def consumerRecordDecoder(cr: ConsumerRecord[String, Array[Byte]]): DecodeScheduleResult =
    Option(cr.value) match {
      case Some(bytes) =>
        for {
          scheduleTry <- Either.fromOption(valueDecoder(bytes), InvalidSchemaError(cr.key))
          schedule <- scheduleTry.toEither.leftMap(t => AvroMessageFormatError(cr.key, t))
        } yield {
          logger.info(s"Received schedule with ID: ${cr.key} to be sent to topic: ${schedule.topic} at time: ${schedule.time}")
          (cr.key, Some(schedule))
        }
      case None =>
        Right((cr.key, None))
    }

  private def valueDecoder(avro: Array[Byte]): Option[Try[Schedule]] =
    AvroInputStream.binary[Schedule](avro).tryIterator.toSeq.headOption
}
