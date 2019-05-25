package com.sky.kms

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon

object ShutdownTasks extends LazyLogging {

  def forScheduler(running: SchedulerApp.Running)(implicit system: ActorSystem): Unit =
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "shutdown-scheduler") { () =>
      import system.dispatcher

      logger.info("Shutting down KMS streams")
      running.publisher.materializedSource.complete()

      for {
        control <- running.reader.mat
        done <- control.shutdown()
      } yield done
    }

  def forKamon(implicit system: ActorSystem): Unit =
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "shutdown-kamon") { () =>
      logger.info("Shutting down Kamon")
      Kamon.stopAllReporters().map(_ => Done)(system.dispatcher)
    }

}
