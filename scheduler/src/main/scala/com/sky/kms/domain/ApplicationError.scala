package com.sky.kms.domain

import akka.stream.scaladsl.{Flow, Sink}
import akka.{Done, NotUsed}
import cats.Show._
import cats.syntax.comonad._
import cats.syntax.show._
import cats.{Comonad, Show}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.language.higherKinds

sealed abstract class ApplicationError(key: String)

object ApplicationError extends LazyLogging {

  case class InvalidSchemaError(key: String) extends ApplicationError(key)

  implicit val showInvalidSchemaError: Show[InvalidSchemaError] =
    show(error => s"Invalid schema used to produce message with key: ${error.key}")

  case class AvroMessageFormatError(key: String, cause: Throwable)
    extends ApplicationError(key)

  implicit val showAvroMessageFormatError: Show[AvroMessageFormatError] =
    show(error => s"Error when processing message with key: ${error.key}. Error message: ${error.cause.getMessage}")

  implicit val showError: Show[ApplicationError] = show {
    case schemaError: InvalidSchemaError => showInvalidSchemaError.show(schemaError)
    case messageFormatError: AvroMessageFormatError => showAvroMessageFormatError.show(messageFormatError)
  }

  def extractError[F[_] : Comonad, T]: Flow[F[Either[ApplicationError, T]], ApplicationError, NotUsed] =
    Flow[F[Either[ApplicationError, T]]]
      .map(_.extract)
      .collect { case Left(error) => error }

  val logErrors: Sink[ApplicationError, Future[Done]] = Sink.foreach(error => logger.warn(error.show))
}
