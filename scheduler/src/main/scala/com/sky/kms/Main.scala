package com.sky.kms

import cats.implicits.toShow
import com.sky.BuildInfo
import com.sky.kms.config.AppConfig
import com.typesafe.scalalogging.LazyLogging
import eu.timepit.refined.pureconfig._
import pureconfig.ConfigSource.default.loadOrThrow
import pureconfig.generic.auto._
import pureconfig.module.cats._

object Main extends App with LazyLogging with AkkaComponents {
  logger.info(s"Kafka Message Scheduler ${BuildInfo.name} ${BuildInfo.version} starting up...")

  val conf: AppConfig = loadOrThrow[AppConfig]

  logger.info(s"Loaded Config ${conf.show}")

  SchedulerApp.metricsInit

  val app        = SchedulerApp.configure.apply(conf)
  val runningApp = SchedulerApp.run.apply(app)

  logger.info("Kafka Message Scheduler initialisation complete.")
}
