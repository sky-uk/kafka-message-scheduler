package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging

trait AkkaComponents extends LazyLogging {

  implicit val system = ActorSystem("kafka-message-scheduler")

  implicit val materializer = ActorMaterializer()

}
