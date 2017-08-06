package com.sky.kafka.message

import cats.syntax.either._
import com.sksamuel.avro4s.AvroInputStream
import com.sky.kafka.message.scheduler.domain.ApplicationError._
import com.sky.kafka.message.scheduler.domain.{ApplicationError, _}
import com.sky.kafka.message.scheduler.kafka.ConsumerRecordDecoder
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import com.sky.kafka.message.scheduler.avro._
import com.sky.kafka.message.scheduler.domain.ScheduleData.Schedule

import scala.util.Try

package object scheduler extends LazyLogging {

  type DecodeResult = Either[ApplicationError, (ScheduleId, Option[Schedule])]

  implicit val scheduleConsumerRecordDecoder = new ConsumerRecordDecoder[DecodeResult] {
    def apply(cr: ConsumerRecord[String, Array[Byte]]): DecodeResult =
      consumerRecordDecoder(cr)
  }

  def consumerRecordDecoder(cr: ConsumerRecord[String, Array[Byte]]): DecodeResult =
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
