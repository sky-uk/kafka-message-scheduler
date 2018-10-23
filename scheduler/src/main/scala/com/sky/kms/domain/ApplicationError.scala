package com.sky.kms.domain

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Sink}
import cats.Show
import cats.Show._
import cats.syntax.show._
import com.typesafe.scalalogging.LazyLogging
import shapeless.{TypeCase, Typeable}

import scala.language.higherKinds

sealed abstract class ApplicationError(key: String)

object ApplicationError extends LazyLogging {

  case class InvalidSchemaError(key: String) extends ApplicationError(key)

  implicit val showInvalidSchemaError: Show[InvalidSchemaError] =
    show(error => s"Invalid schema used to produce message with key: ${error.key}")

  case class AvroMessageFormatError(key: String, cause: Throwable) extends ApplicationError(key)

  implicit val showAvroMessageFormatError: Show[AvroMessageFormatError] =
    show(error => s"Error when processing message with key: ${error.key}. Error message: ${error.cause.getMessage}")

  implicit val showError: Show[ApplicationError] = show {
    case schemaError: InvalidSchemaError => showInvalidSchemaError.show(schemaError)
    case messageFormatError: AvroMessageFormatError => showAvroMessageFormatError.show(messageFormatError)
  }

  implicit def errorSink[F[_]: Typeable]: Sink[F[Either[ApplicationError, _]], NotUsed] = {
    val fEither = TypeCase[F[Either[ApplicationError, _]]]
    Flow[F[Either[ApplicationError, _]]].collect { case fEither(error) => error }.to(Sink.foreach(error => logger.warn(error.show)))
  }
}
