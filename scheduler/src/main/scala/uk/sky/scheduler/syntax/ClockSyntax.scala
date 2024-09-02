package uk.sky.scheduler.syntax

import cats.Applicative
import cats.effect.Clock
import cats.syntax.all.*

import java.time.Instant

trait ClockSyntax {
  extension [F[_] : Applicative](c: Clock[F]) {
    def epochMilli: F[Long] =
      c.realTimeInstant.map(_.toEpochMilli)

    def epochMilli(f: Instant => Instant): F[Long] =
      c.realTimeInstant.map(f andThen (_.toEpochMilli))
  }
}

object clock extends ClockSyntax
