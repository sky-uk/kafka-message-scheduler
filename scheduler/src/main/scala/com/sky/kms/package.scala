package com.sky

import cats.Eval
import cats.data.{Kleisli, ReaderT}
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

  implicit val scheduleConsumerRecordDecoder: ConsumerRecordDecoder[Either[ApplicationError, (ScheduleId, Option[Schedule])]] =
    ConsumerRecordDecoder.instance(consumerRecordDecoder)

  def consumerRecordDecoder(cr: ConsumerRecord[String, Array[Byte]]): Either[ApplicationError, (ScheduleId, Option[Schedule])] =
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

  type Start[T] = ReaderT[Eval, SchedulerApp, T]

  object Start {
    def apply[T](f: SchedulerApp => Eval[T]): Start[T] = Kleisli(f)
  }

}
