package com.sky.kms.monitoring

import kamon.Kamon

trait StartupGauge {
  def onStateChange(state: StartupGauge.State): Unit
}

object StartupGauge {
  def kamon(): StartupGauge = new StartupGauge {
    private val gauge = Kamon.gauge("scheduler_startup").withoutTags()

    override def onStateChange(state: State): Unit = state match {
      case Loading => gauge.update(0)
      case Ready   => gauge.update(1)
      case Failed  => gauge.update(2)
    }
  }

  sealed trait State extends Product with Serializable

  case object Loading extends State
  case object Ready   extends State
  case object Failed  extends State
}
