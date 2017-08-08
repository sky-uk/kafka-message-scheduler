package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.Supervision.Restart
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.typesafe.scalalogging.LazyLogging

trait AkkaComponents extends LazyLogging with Monitoring {

  implicit val system = ActorSystem("kafka-message-scheduler")

  val decider: Supervision.Decider = { t =>
    recordException(t)
    logger.error(s"Supervision failed.", t)
    Restart
  }

  val settings = ActorMaterializerSettings(system)
    .withSupervisionStrategy(decider)

  implicit val materializer = ActorMaterializer(settings)

}
