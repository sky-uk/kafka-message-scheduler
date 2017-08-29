package com.sky

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
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

  implicit class ReaderOps[A, B](val reader: Reader[A, B]) extends AnyVal {
    def <~(a: A)(implicit system: ActorSystem, mat: ActorMaterializer): B =
      reader.run(a)
  }
}
