package com.sky.kms.monitoring

import kamon.Kamon

trait StartupGauge {
  def onStateChange(state: StartupState): Unit
}

object StartupGauge {
  def kamon(): StartupGauge = new StartupGauge {
    private val gauge = Kamon.gauge("scheduler-startup").withoutTags()

    override def onStateChange(state: StartupState): Unit = state match {
      case StartupState.Loading => gauge.update(0)
      case StartupState.Ready   => gauge.update(1)
      case StartupState.Failed  => gauge.update(2)
    }
  }
}

sealed trait StartupState extends Product with Serializable

object StartupState {
  case object Loading extends StartupState
  case object Ready   extends StartupState
  case object Failed  extends StartupState
}
