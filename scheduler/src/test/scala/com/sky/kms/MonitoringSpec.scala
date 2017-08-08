package com.sky.kms

import common.BaseSpec
import kamon.metric.MetricsModule
import kamon.metric.instrument.Counter
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

class MonitoringSpec extends BaseSpec with MockitoSugar {

  "increment" should {
    "increment the counter for the given key" in new MonitoringFixtures {
      val key = "test-key"

      when(mockMetrics.counter(key)).thenReturn(mockCounter)

      monitoring.increment(key)

      verify(mockMetrics).counter(key)
      verify(mockCounter).increment()
    }
  }

  "recordException" should {
    "increment the counter for a key based on the exception type" in new MonitoringFixtures {
      val key = "exception.java_lang_Exception"
      val exception = new Exception
      when(mockMetrics.counter(key)).thenReturn(mockCounter)

      monitoring.recordException(exception)

      verify(mockMetrics).counter(key)
      verify(mockCounter).increment()
    }
  }

  private class MonitoringFixtures {
    val mockCounter = mock[Counter]
    val mockMetrics = mock[MetricsModule]

    val monitoring = new Monitoring(){
      override val metrics: MetricsModule = mockMetrics
    }
  }

}
