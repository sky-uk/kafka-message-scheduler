package com.sky.kms.utils

import java.util.concurrent.atomic.AtomicLong

import com.sky.kms.monitoring.ScheduleGauge

class MockGauge extends ScheduleGauge {

  val counter: AtomicLong = new AtomicLong()

  override def onUpdate(): Unit = counter.incrementAndGet()

  override def onDelete(): Unit = counter.decrementAndGet()

}
