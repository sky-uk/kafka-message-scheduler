package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.Supervision.Stop
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.typesafe.scalalogging.LazyLogging

trait AkkaComponents extends LazyLogging {

  implicit lazy val system = ActorSystem("kafka-message-scheduler")

  implicit lazy val materializer = ActorMaterializer(ActorMaterializerSettings(system).withSupervisionStrategy(t => {
    logger.error("Exception caught by stream supervisor", t)
    Stop
  }))

}
