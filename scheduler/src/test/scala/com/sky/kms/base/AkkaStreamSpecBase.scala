package com.sky.kms.base

import akka.stream.ActorMaterializer
import com.sky.kms.BackoffRestartStrategy
import com.sky.kms.BackoffRestartStrategy.Restarts
import eu.timepit.refined.auto._

import scala.concurrent.duration._

abstract class AkkaStreamSpecBase extends AkkaSpecBase {

  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    materializer.shutdown()
  }

  val noRestarts = BackoffRestartStrategy(10.millis, 10.millis, Restarts(0))
}
