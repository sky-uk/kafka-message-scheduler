package com.sky.kms.base

import akka.stream.ActorMaterializer

abstract class AkkaStreamSpecBase extends AkkaSpecBase {

  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    materializer.shutdown()
  }
}
