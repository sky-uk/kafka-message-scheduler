package com.sky.kafka.message.scheduler

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.ActorMaterializer
import com.sky.kafka.message.scheduler.kafka._
import com.typesafe.scalalogging.LazyLogging

case class SchedulerStream(config: SchedulerConfig)(implicit system: ActorSystem, materializer: ActorMaterializer) extends LazyLogging {

  def run: Control =
    consumeFromKafka(config.scheduleTopic)
      .map {
        case Right((scheduleId, Some(schedule))) =>
          logger.info(s"Publishing scheduled message with ID: $scheduleId to topic: ${schedule.topic}")
          schedule
      } // match not exhaustive as pending error handling
      .writeToKafka
      .run()

}
