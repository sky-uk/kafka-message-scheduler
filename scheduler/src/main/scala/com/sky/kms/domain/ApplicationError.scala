package com.sky.kms.domain

import java.time.OffsetDateTime

import akka.stream.scaladsl.{Flow, Sink}
import akka.{Done, NotUsed}
import cats.Show
import cats.Show.*
import cats.syntax.show.*
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

sealed trait ApplicationError extends Product with Serializable

object ApplicationError extends LazyLogging {

  final case class InvalidSchemaError(key: String) extends ApplicationError

  final case class AvroMessageFormatError(key: String, cause: Throwable) extends ApplicationError

  final case class InvalidTimeError(key: String, time: OffsetDateTime) extends ApplicationError

  implicit val showError: Show[ApplicationError] = show {
    case error: InvalidSchemaError     => s"Invalid schema used to produce message with key ${error.key}"
    case error: AvroMessageFormatError =>
      s"Error when processing message with key ${error.key}. ${error.cause.getMessage}"
    case error: InvalidTimeError       =>
      s"Time between now and ${error.time} is not within 292 years on message ${error.key}"
  }

  def extractError[T]: Flow[Either[ApplicationError, T], ApplicationError, NotUsed] =
    Flow[Either[ApplicationError, T]].collect { case Left(error) => error }

  val logErrors: Sink[ApplicationError, Future[Done]] = Sink.foreach(error => logger.warn(error.show))
}
