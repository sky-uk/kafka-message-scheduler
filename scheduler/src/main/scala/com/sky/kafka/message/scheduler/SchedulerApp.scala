package com.sky.kafka.message.scheduler

import com.sky.kafka.message.scheduler.streams.ScheduleReader
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon
import pureconfig._

import scala.concurrent.Await

object SchedulerApp extends App with AkkaComponents with LazyLogging {

  val conf = loadConfigOrThrow[AppConfig]
  Kamon.start()

  logger.info("Kafka Message Scheduler starting up...")
  val app = ScheduleReader.reader(conf)

  val runningApp = app.stream.run()

  sys.addShutdownHook {
    val shutdownTimeout = conf.scheduler.shutdownTimeout
    logger.info("Kafka Message Scheduler shutting down...")

    Await.ready(runningApp.shutdown(), shutdownTimeout.stream)
    Await.ready({
      app.queue.complete()
      app.queue.watchCompletion()
    }, shutdownTimeout.stream)
    Await.ready(system.terminate(), shutdownTimeout.system)
    Kamon.shutdown()
  }

}