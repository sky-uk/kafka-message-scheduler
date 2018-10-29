package com.sky.kms.base

import com.sky.kms.utils.TestActorSystem

trait AkkaKafkaSpecBase extends AkkaStreamSpecBase with KafkaIntSpecBase {
  override implicit lazy val system = TestActorSystem(kafkaConfig.kafkaPort)
}
