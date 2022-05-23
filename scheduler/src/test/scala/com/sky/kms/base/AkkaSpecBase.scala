package com.sky.kms.base

import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestKitBase}
import com.sky.kms.utils.TestActorSystem

abstract class AkkaSpecBase extends SpecBase with TestKitBase {

  override implicit lazy val system: ActorSystem = TestActorSystem()

  override def afterAll(): Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }
}
