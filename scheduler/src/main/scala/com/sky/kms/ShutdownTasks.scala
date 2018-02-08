package com.sky.kms

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import kamon.Kamon

import scala.concurrent.Future

object ShutdownTasks {

  def forScheduler(running: SchedulerApp.Running)(implicit system: ActorSystem): Unit =
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "shutdown-scheduler") { () =>
      running.publisher.materializedSource.complete()
      running.reader.materializedSource.shutdown()
      Future.successful(Done)
    }

  def forKamon(implicit system: ActorSystem): Unit =
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "shutdown-kamon") { () =>
      Kamon.shutdown()
      Future.successful(Done)
    }

}
