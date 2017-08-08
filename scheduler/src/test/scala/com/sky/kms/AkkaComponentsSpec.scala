package com.sky.kms

import akka.stream.Supervision.Restart
import common.BaseSpec
import kamon.metric.MetricsModule
import kamon.metric.instrument.Counter
import org.mockito.Mockito.{verify, when}
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.Await
import scala.concurrent.duration._

class AkkaComponentsSpec extends BaseSpec with MockitoSugar {

  "decider" should {
    "collect metrics from failures" in new AkkaComponentsFixture {
      val key = "exception.java_lang_Exception"
      val exception = new Exception("Any exception")

      when(metrics.counter(key)).thenReturn(mockCounter)

      materializer
        .settings
        .supervisionDecider(exception) shouldBe Restart

      verify(metrics).counter(key)
      verify(mockCounter).increment()

      materializer.shutdown()
      Await.ready(system.terminate(), 5 seconds)
    }
  }

  private class AkkaComponentsFixture extends AkkaComponents {
    val mockCounter = mock[Counter]
    override val metrics: MetricsModule = mock[MetricsModule]
  }
}
