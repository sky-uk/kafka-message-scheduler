package com.sky.kms.utils

import java.util.concurrent.atomic.AtomicReference

import com.sky.kms.monitoring.{StartupGauge, StartupState}

class MockStartupGauge extends StartupGauge {

  val currentState = new AtomicReference[StartupState]()

  override def onStateChange(state: StartupState): Unit = currentState.set(state)
}
