package com.sky.kms

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import kamon.Kamon

import scala.concurrent.Future

object ShutdownTasks {

  def scheduler(running: SchedulerApp.Running)(implicit system: ActorSystem): Unit =
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "shutdown-scheduler") { () =>
      running.runningPublisher.materializedSource.complete()
      running.runningReader.materializedSource.shutdown()
    }

  def kamon(implicit system: ActorSystem): Unit =
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "shutdown-kamon") { () =>
      Kamon.shutdown()
      Future.successful(Done)
    }

}
