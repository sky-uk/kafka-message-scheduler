package uk.sky.scheduler

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.typelevel.otel4s.metrics.Meter

abstract class AsyncSpecBase extends AsyncWordSpec, AsyncIOSpec, Matchers {
  given noopMeter: Meter[IO] = Meter.noop[IO]
}
