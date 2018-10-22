package com.sky.kms.utils

import java.util.concurrent.atomic.AtomicLong

import com.sky.kms.monitoring.Monitoring

class SimpleCounterMonitoring extends Monitoring {
  val scheduleReceivedCounter = new AtomicLong(0)

  val scheduleDoneCounter = new AtomicLong(0)

  override def scheduleReceived(): Unit = scheduleReceivedCounter.incrementAndGet()

  override def scheduleDone(): Unit = scheduleDoneCounter.incrementAndGet()

}
