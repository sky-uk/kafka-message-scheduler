package com.sky.kms.utils

import java.util.concurrent.atomic.AtomicReference

import com.sky.kms.monitoring.StartupGauge

class MockStartupGauge extends StartupGauge {

  val currentState = new AtomicReference[StartupGauge.State]()

  override def onStateChange(state: StartupGauge.State): Unit = currentState.set(state)
}
