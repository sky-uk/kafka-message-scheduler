package com.sky.kafka.message.scheduler.domain
import akka.Done
import akka.stream.scaladsl.Sink
import cats.Show
import cats.Show._
import cats.syntax.show._
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

sealed abstract class ApplicationError(key: String)

object ApplicationError extends LazyLogging {

  case class InvalidSchemaError(key: String) extends ApplicationError(key)

  implicit val invalidSchemaErrorShow: Show[InvalidSchemaError] =
    show(error => s"Invalid schema used to produce message with key: ${error.key}")


  case class AvroMessageFormatError(key: String, cause: Throwable) extends ApplicationError(key)

  implicit val avroMessageFormatErrorShow: Show[AvroMessageFormatError] =
    show(error => s"Error when processing message with key: ${error.key}. Error message: ${error.cause.getMessage}")


  implicit val showError: Show[ApplicationError] = show {
    case schemaError: InvalidSchemaError => invalidSchemaErrorShow.show(schemaError)
    case messageFormatError: AvroMessageFormatError => avroMessageFormatErrorShow.show(messageFormatError)
  }

  //TODO: is this enough for now?
  implicit val errorSink: Sink[ApplicationError, Future[Done]] =
    Sink.foreach(error => logger.warn(error.show))
}
