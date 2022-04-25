package com.sky.kms

import akka.actor.ActorSystem

trait AkkaComponents {

  implicit lazy val system = ActorSystem("kafka-message-scheduler")

}
