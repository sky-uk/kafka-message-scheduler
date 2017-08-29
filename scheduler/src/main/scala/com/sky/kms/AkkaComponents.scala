package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.sky.kms.config.ShutdownTimeout

import scala.concurrent.Await

trait AkkaComponents {

  implicit val system = ActorSystem("kafka-message-scheduler")

  implicit val materializer = ActorMaterializer()

}

object AkkaComponents extends AkkaComponents {
  def stop()(implicit timeout: ShutdownTimeout) {
    materializer.shutdown()
    Await.ready(system.terminate(), timeout.system)
  }
}
