package com.sky.kms.monitoring

trait Monitoring {

  def scheduleReceived(): Unit

  def scheduleDone(): Unit
}
