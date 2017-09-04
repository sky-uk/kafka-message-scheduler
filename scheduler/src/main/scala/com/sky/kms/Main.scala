package com.sky.kms

import com.sky.BuildInfo
import com.sky.kms.config.AppConfig
import com.typesafe.scalalogging.LazyLogging
import pureconfig._

import scala.concurrent.Await

object Main extends App with LazyLogging with AkkaComponents {

  val conf = loadConfigOrThrow[AppConfig]

  logger.info(s"Kafka Message Scheduler ${BuildInfo.name} ${BuildInfo.version} starting up...")

  val app = SchedulerApp.configure apply conf

  val runningApp = SchedulerApp.run apply app

  sys.addShutdownHook {
    logger.info("Kafka Message Scheduler shutting down...")

    import scala.concurrent.ExecutionContext.Implicits.global
    Await.ready(SchedulerApp.stop apply runningApp, conf.scheduler.shutdownTimeout)
  }

  logger.info("Kafka Message Scheduler initialisation complete.")
}
