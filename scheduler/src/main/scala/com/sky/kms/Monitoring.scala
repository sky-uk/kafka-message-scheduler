package com.sky.kms

import kamon.Kamon
import kamon.metric.MetricsModule

trait Monitoring {

  val metrics: MetricsModule = Kamon.metrics

  def increment(key: String) = metrics.counter(key).increment()

  def recordException(throwable: Throwable) = {
    val key = generateKeyFromException(throwable)
    metrics.counter(key).increment()
  }

  private def generateKeyFromException(throwable: Throwable): String = {
    return s"exception.${throwable.getClass.getName.replace(".", "_")}"
  }
}
