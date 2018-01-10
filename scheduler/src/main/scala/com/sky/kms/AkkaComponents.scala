package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

trait AkkaComponents {

  implicit val system = ActorSystem("kafka-message-scheduler")

  implicit val materializer = ActorMaterializer()

}
