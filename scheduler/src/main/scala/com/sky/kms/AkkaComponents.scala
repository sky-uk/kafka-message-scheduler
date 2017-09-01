package com.sky.kms

import akka.actor.{ActorSystem, Terminated}
import akka.stream.ActorMaterializer

trait AkkaComponents {

  implicit val system = ActorSystem("kafka-message-scheduler")

  implicit val materializer = ActorMaterializer()

}

object AkkaComponents extends AkkaComponents {

  def stop(): Stop[Terminated] = Stop { _ =>
    materializer.shutdown()
    system.terminate()
  }
}
