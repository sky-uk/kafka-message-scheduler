package com.sky.kms.base

import akka.actor.ActorSystem
import com.sky.kms.utils.TestActorSystem

trait AkkaKafkaSpecBase extends AkkaSpecBase with KafkaIntSpecBase {
  override implicit lazy val system: ActorSystem = TestActorSystem(kafkaConfig.kafkaPort)
}
