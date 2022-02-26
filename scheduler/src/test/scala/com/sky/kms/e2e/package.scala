package com.sky.kms

import akka.actor.CoordinatedShutdown.UnknownReason
import akka.actor.{ActorSystem, CoordinatedShutdown}
import com.sky.kms.config.{AppConfig, SchedulerConfig}

package object e2e {

  def withSchedulerApp[T](
      scenario: => T
  )(implicit conf: SchedulerConfig, system: ActorSystem): T =
    withRunningScheduler(SchedulerApp.configure apply AppConfig(conf))(_ => scenario)

  def withRunningScheduler[T](
      schedulerApp: SchedulerApp
  )(scenario: SchedulerApp.Running => T)(implicit system: ActorSystem): T = {
    val runningApp = SchedulerApp.run apply schedulerApp

    try
      scenario(runningApp)
    finally
      CoordinatedShutdown(system).run(UnknownReason)
  }

}
