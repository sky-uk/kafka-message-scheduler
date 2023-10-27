package com.sky.kms.monitoring

import kamon.Kamon

trait ScheduleGauge {

  def onUpdate(): Unit

  def onDelete(): Unit

}

object ScheduleGauge {
  def kamon(): ScheduleGauge = new ScheduleGauge {

    private val gauge = Kamon.gauge("scheduler-messages").withTag("status", "scheduled")

    override def onUpdate(): Unit = gauge.increment()

    override def onDelete(): Unit = gauge.decrement()
  }
}
