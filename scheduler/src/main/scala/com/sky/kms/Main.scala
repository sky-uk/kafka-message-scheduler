package com.sky.kms

import com.sky.BuildInfo
import com.sky.kms.config.AppConfig
import com.typesafe.scalalogging.LazyLogging
import eu.timepit.refined.pureconfig._
import pureconfig._
import pureconfig.module.cats._

object Main extends App with LazyLogging with AkkaComponents {

  val conf = loadConfigOrThrow[AppConfig]

  logger.info(s"Kafka Message Scheduler ${BuildInfo.name} ${BuildInfo.version} starting up...")

  val app = SchedulerApp.configure apply conf

  val runningApp = SchedulerApp.run apply app

  logger.info("Kafka Message Scheduler initialisation complete.")
}
