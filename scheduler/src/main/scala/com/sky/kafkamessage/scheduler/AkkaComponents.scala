package com.sky.kafkamessage.scheduler

import akka.actor.ActorSystem
import akka.stream.Supervision.Restart
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.typesafe.scalalogging.LazyLogging

trait AkkaComponents extends LazyLogging {

  implicit val system = ActorSystem("kafka-message-scheduler")

  val decider: Supervision.Decider = { t =>
    logger.error(s"Supervision failed.", t)
    Restart
  }

  val settings = ActorMaterializerSettings(system)
    .withSupervisionStrategy(decider)

  implicit val materializer = ActorMaterializer(settings)

}
