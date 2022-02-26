package com.sky.kms

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext

trait AkkaComponents extends LazyLogging {

  implicit lazy val system: ActorSystem  = ActorSystem("kafka-message-scheduler")
  implicit lazy val ec: ExecutionContext = system.dispatcher

}
