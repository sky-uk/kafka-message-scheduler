package com.sky.kms.common

import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterAll

abstract class AkkaBaseSpec extends TestKit(TestActorSystem())
  with BaseSpec with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    shutdown(system, verifySystemShutdown = true)
    super.afterAll()
  }
}
