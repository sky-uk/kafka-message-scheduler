package com.sky.kms

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging

trait AkkaComponents extends LazyLogging {

  implicit lazy val system: ActorSystem = ActorSystem("kafka-message-scheduler")

}
