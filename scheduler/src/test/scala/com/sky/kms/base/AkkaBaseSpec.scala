package com.sky.kms.base

import akka.testkit.{TestKit, TestKitBase}
import com.sky.kms.common.TestActorSystem

abstract class AkkaBaseSpec extends TestKitBase with BaseSpec {

  override implicit lazy val system = TestActorSystem()

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }
}
