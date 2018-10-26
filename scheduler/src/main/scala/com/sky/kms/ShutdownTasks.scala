package com.sky.kms

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon
import kamon.system.SystemMetrics

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object ShutdownTasks extends LazyLogging {

  def forScheduler(running: SchedulerApp.Running)(implicit system: ActorSystem): Unit = {
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeServiceUnbind, "shutdown-scheduler") { () =>
      logger.info("Shutting down KMS streams")
      running.publisher.materializedSource.complete()
      running.reader.materializedSource.shutdown()
      Future.successful(Done)
    }
  }

  def forKamon(implicit system: ActorSystem): Unit =
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "shutdown-kamon") { () =>
      logger.info("Shutting down Kamon")
      SystemMetrics.stopCollecting()
      Kamon.stopAllReporters().map(_ => Done)
    }

}
