package com.sky.kafka.message.scheduler

import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon

import scala.concurrent.Await
import pureconfig._

object SchedulerApp extends App with AkkaComponents with LazyLogging {

  val conf = loadConfigOrThrow[AppConfig].scheduler
  Kamon.start()

  logger.info("Kafka Message Scheduler starting up...")
  val runningStream = SchedulerStream(conf).run

  sys.addShutdownHook {
    logger.info("Kafka Message Scheduler shutting down...")
    Await.ready(runningStream.shutdown(), conf.shutdownTimeout.stream)
    Await.ready(system.terminate(), conf.shutdownTimeout.system)
    Kamon.shutdown()
  }

}