package com.sky.kms

import com.sky.BuildInfo
import com.sky.kms.config.AppConfig
import com.typesafe.scalalogging.LazyLogging
import pureconfig._
import AkkaComponents._

object Main extends App with LazyLogging with AkkaComponents {

  val conf = loadConfigOrThrow[AppConfig]

  implicit val timeouts = conf.scheduler.shutdownTimeout

  logger.info(s"Kafka Message Scheduler ${BuildInfo.name} ${BuildInfo.version} starting up...")

  val app = SchedulerApp.reader akka conf

  val runningApp = SchedulerApp.run akka app

  sys.addShutdownHook {
    logger.info("Kafka Message Scheduler shutting down...")
    SchedulerApp.stop akka runningApp
  }

  logger.info("Kafka Message Scheduler initialisation complete.")
}
