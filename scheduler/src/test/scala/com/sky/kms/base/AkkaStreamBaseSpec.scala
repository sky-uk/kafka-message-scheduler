package com.sky.kms.base

import akka.stream.ActorMaterializer

abstract class AkkaStreamBaseSpec extends AkkaBaseSpec {

  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    super.afterAll()
    materializer.shutdown()
  }
}
