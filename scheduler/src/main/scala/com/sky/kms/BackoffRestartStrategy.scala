package com.sky.kms

import eu.timepit.refined.api.Refined
import eu.timepit.refined.boolean.Not
import eu.timepit.refined.numeric.Negative

import scala.concurrent.duration.FiniteDuration

case class BackoffRestartStrategy(minBackoff: FiniteDuration,
                                  maxBackoff: FiniteDuration,
                                  maxRestarts: BackoffRestartStrategy.RestartsAmount)

object BackoffRestartStrategy {
  sealed trait RestartsAmount
  case class Restarts(amount: Int Refined Not[Negative]) extends RestartsAmount
  case object InfiniteRestarts                           extends RestartsAmount
}
