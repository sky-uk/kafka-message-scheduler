package uk.sky.scheduler.util

import java.time.Instant

import cats.effect.Async
import cats.effect.syntax.all.*
import cats.syntax.all.*
import org.scalactic.source
import org.scalatest.exceptions.{StackDepthException, TestFailedException}

import scala.concurrent.duration.*

object testSyntax {
  extension [F[_] : Async, A](fa: F[A]) {
    def testTimeout(timeout: FiniteDuration = 10.seconds)(using pos: source.Position): F[A] =
      fa.timeoutTo(
        timeout,
        TestFailedException(
          (_: StackDepthException) => s"Operation did not complete within $timeout".some,
          none,
          pos
        ).raiseError[F, A]
      )

    def realTimeInstant: F[(Instant, A)] =
      for {
        a   <- fa
        now <- Async[F].realTimeInstant
      } yield now -> a
  }
}
