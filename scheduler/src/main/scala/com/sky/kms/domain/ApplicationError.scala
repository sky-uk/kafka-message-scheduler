package com.sky.kms.domain

import java.time.OffsetDateTime

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

  final case class InvalidSchemaError(key: String) extends ApplicationError(key)

  final case class AvroMessageFormatError(key: String, cause: Throwable)
    extends ApplicationError(key)

  final case class InvalidTimeError(key: String, time: OffsetDateTime) extends ApplicationError(key)

  implicit val showError: Show[ApplicationError] = show {
    case error: InvalidSchemaError => s"Invalid schema used to produce message with key ${error.key}"
    case error: AvroMessageFormatError => s"Error when processing message with key ${error.key}. ${error.cause.getMessage}"
    case error: InvalidTimeError => s"Time between now and ${error.time} is not within 292 years"
  }

  def extractError[F[_] : Comonad, T]: Flow[F[Either[ApplicationError, T]], ApplicationError, NotUsed] =
    Flow[F[Either[ApplicationError, T]]]
      .map(_.extract)
      .collect { case Left(error) => error }

  val logErrors: Sink[ApplicationError, Future[Done]] = Sink.foreach(error => logger.warn(error.show))
}
