package com.sky.kafka.message.scheduler

import com.sky.kafka.message.scheduler.config.AppConfig
import com.sky.kafka.message.scheduler.streams.ScheduleReader
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon
import org.zalando.grafter._
import pureconfig._

import scala.concurrent.Await

object SchedulerApp extends App with LazyLogging with AkkaComponents {

  val conf = loadConfigOrThrow[AppConfig]
  Kamon.start()

  logger.info("Kafka Message Scheduler starting up...")
  val app = ScheduleReader.reader.run(conf)

  sys.addShutdownHook {
    logger.info("Kafka Message Scheduler shutting down...")
    Rewriter.stop(app).value

    materializer.shutdown()
    Await.result(system.terminate(), conf.scheduler.shutdownTimeout.system)

    Kamon.shutdown()
  }
}