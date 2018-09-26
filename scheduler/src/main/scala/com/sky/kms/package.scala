package com.sky

import cats.data.Reader
import cats.syntax.either._
import com.sksamuel.avro4s.AvroInputStream
import com.sky.kms.avro._
import com.sky.kms.domain.ApplicationError._
import com.sky.kms.domain._
import com.sky.kms.kafka.ConsumerRecordDecoder
import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.util.Try

package object kms extends LazyLogging {

  implicit val scheduleConsumerRecordDecoder: ConsumerRecordDecoder[Either[ApplicationError, (ScheduleId, Option[ScheduleEvent])]] =
    ConsumerRecordDecoder.instance(consumerRecordDecoder)

  def consumerRecordDecoder(cr: ConsumerRecord[String, Array[Byte]]): Either[ApplicationError, (ScheduleId, Option[ScheduleEvent])] =
    Option(cr.value) match {
      case Some(bytes) =>
        for {
          scheduleTry <- Either.fromOption(valueDecoder(bytes), InvalidSchemaError(cr.key))
          avroSchedule <- scheduleTry.toEither.leftMap(t => AvroMessageFormatError(cr.key, t))
        } yield {
          val schedule = ScheduleEvent(avroSchedule.time, cr.topic(), avroSchedule.topic, avroSchedule.key, avroSchedule.value)
          logger.info(s"Received schedule from topic: ${schedule.inputTopic} with ID: ${cr.key} to be sent to topic: ${schedule.outputTopic} at time: ${schedule.time}")
          (cr.key, Some(schedule))
        }
      case None =>
        Right((cr.key, None))
    }

  private def valueDecoder(avro: Array[Byte]): Option[Try[Schedule]] =
    AvroInputStream.binary[Schedule](avro).tryIterator.toSeq.headOption

  type Start[T] = Reader[SchedulerApp, T]

  object Start {
    def apply[T](f: SchedulerApp => T): Start[T] = Reader(f)
  }

}
