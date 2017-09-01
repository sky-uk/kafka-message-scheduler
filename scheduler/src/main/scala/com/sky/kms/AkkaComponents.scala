package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.Reader
import com.sky.kms.config.ShutdownTimeout

import scala.concurrent.Await

trait AkkaComponents {

  implicit val system = ActorSystem("kafka-message-scheduler")

  implicit val materializer = ActorMaterializer()

}

object AkkaComponents extends AkkaComponents {

  implicit class AkkaReader[A, B](val reader: Reader[A, B]) extends AnyVal {
    def akka(a: A)(implicit system: ActorSystem, mat: ActorMaterializer): B =
      reader.run(a)
  }

  def stop()(implicit timeout: ShutdownTimeout) {
    materializer.shutdown()
    Await.ready(system.terminate(), timeout.system)
  }
}
