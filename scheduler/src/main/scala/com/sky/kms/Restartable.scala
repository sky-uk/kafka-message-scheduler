package com.sky.kms

import akka.stream.scaladsl.{RestartSource, Source}
import akka.{Done, NotUsed}
import com.sky.kms.BackoffRestartStrategy.{InfiniteRestarts, Restarts}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.util.Try

object Restartable extends LazyLogging {
  def apply[A, B](source: => Source[A, B])(bos: BackoffRestartStrategy,
                                           onRestart: Try[Done] => Unit = doNothing)(implicit ec: ExecutionContext): Source[A, NotUsed] = {

    val numRestarts = bos.maxRestarts match {
      case Restarts(amount) => amount.value
      case InfiniteRestarts => Int.MaxValue
    }

    RestartSource
      .withBackoff(bos.minBackoff, bos.maxBackoff, randomFactor = 0.2, numRestarts)(() =>
        source.watchTermination() {
          case (_, f) =>
            f.onComplete(onRestart)
        })
  }

  private val doNothing: Try[_] => Unit = _ => ()

  implicit class RestartableSource[A, B, Mat](val source: Source[A, B]) extends AnyVal {
    def restartUsing(bos: BackoffRestartStrategy)(implicit ec: ExecutionContext): Source[A, NotUsed] =
      Restartable(source)(bos, logRestarts)
  }

  private val logRestarts: Try[_] => Unit =
    _.fold(logger.warn("Stream has failed. Restarting.", _), _ => logger.info("Stream has completed. Restarting."))
}
