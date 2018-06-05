package com.sky.kms.e2e

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.actor.CoordinatedShutdown.UnknownReason
import akka.stream.ActorMaterializer
import com.sky.kms.SchedulerApp
import com.sky.kms.config.{AppConfig, SchedulerConfig}
import org.scalatest.Assertion

object RunningSchedulerStream {

  def withRunningSchedulerStream(scenario: => Assertion)(implicit conf: SchedulerConfig, system: ActorSystem, mat: ActorMaterializer) {
    val app = SchedulerApp.configure apply AppConfig(conf)
    SchedulerApp.run apply app

    scenario

    CoordinatedShutdown(system).run(UnknownReason)
  }

}
