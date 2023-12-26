package uk.sky.scheduler.util

import java.time.Instant

import cats.Functor
import cats.effect.syntax.all.*
import cats.effect.{Async, Clock}
import cats.syntax.all.*
import org.scalatest.exceptions.TestFailedException

import scala.concurrent.duration.*

object testSyntax {
  extension [F[_] : Async, A](fa: F[A]) {
    def testTimeout(timeout: FiniteDuration = 10.seconds): F[A] =
      fa.timeoutTo(
        timeout,
        Async[F].raiseError(TestFailedException(s"Operation did not complete within $timeout", 0))
      )

    def realTimeInstant: F[(Instant, A)] =
      for {
        a   <- fa
        now <- Async[F].realTimeInstant
      } yield now -> a
  }

  extension [F[_] : Functor](c: Clock[F]) {
    def epochMilli: F[Long] =
      c.realTimeInstant.map(_.toEpochMilli)

    def epochMilli(f: Instant => Instant): F[Long] =
      c.realTimeInstant.map(f andThen (_.toEpochMilli))
  }
}
