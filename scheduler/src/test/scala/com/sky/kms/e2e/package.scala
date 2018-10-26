package com.sky.kms

import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.actor.CoordinatedShutdown.UnknownReason
import akka.stream.ActorMaterializer
import com.sky.kms.config.{AppConfig, SchedulerConfig}
import org.scalatest.Assertion
import scala.concurrent.duration._

package object e2e {

  val Tolerance = 200 millis

  def withSchedulerApp[T](scenario: => T)(implicit conf: SchedulerConfig, system: ActorSystem, mat: ActorMaterializer): T =
    withRunningScheduler(SchedulerApp.configure apply AppConfig(conf))(_ => scenario)

  def withRunningScheduler[T](schedulerApp: SchedulerApp)(scenario: SchedulerApp.Running => T)(implicit system: ActorSystem, mat: ActorMaterializer): T = {
    val runningApp = SchedulerApp.run apply schedulerApp

    try {
      scenario(runningApp)
    } finally {
      CoordinatedShutdown(system).run(UnknownReason)
    }
  }

}
