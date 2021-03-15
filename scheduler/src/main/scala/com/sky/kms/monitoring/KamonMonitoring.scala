package com.sky.kms.monitoring

import kamon.Kamon

class KamonMonitoring extends Monitoring {

  private val messages          = Kamon.counter("scheduler-messages")
  private val scheduledMessages = messages.withTag("status", "scheduled")
  private val cancelledMessages = messages.withTag("status", "cancelled")

  override def scheduleReceived(): Unit = scheduledMessages.increment()

  override def scheduleDone(): Unit = cancelledMessages.increment()
}
