package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

trait AkkaComponents {

  implicit lazy val system = ActorSystem("kafka-message-scheduler")

  implicit lazy val materializer = ActorMaterializer()

}
