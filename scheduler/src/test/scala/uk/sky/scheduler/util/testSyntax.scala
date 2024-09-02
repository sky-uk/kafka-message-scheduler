package uk.sky.scheduler.util

import cats.effect.Async
import cats.effect.syntax.all.*
import cats.syntax.all.*
import org.scalatest.exceptions.TestFailedException

import java.time.Instant
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
}
