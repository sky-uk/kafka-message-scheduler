package com.sky.kms.utils

import java.util.concurrent.atomic.AtomicReference

import com.sky.kms.monitoring.{StartupGauge, StartupState}

class MockStartupGauge extends StartupGauge {

  val currentState = new AtomicReference[StartupState]()

  override def onStateChange(state: StartupState): Unit = state match {
    case StartupState.Loading => currentState.set(StartupState.Loading)
    case StartupState.Ready   => currentState.set(StartupState.Ready)
    case StartupState.Failed  => currentState.set(StartupState.Failed)
  }
}
