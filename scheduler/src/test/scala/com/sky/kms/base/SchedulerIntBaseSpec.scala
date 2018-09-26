package com.sky.kms.base

import com.sky.kms.common.{KafkaIntSpec, TestActorSystem}
import com.sky.kms.config.SchedulerConfig

abstract class SchedulerIntBaseSpec extends AkkaStreamBaseSpec with KafkaIntSpec {

  val ScheduleTopic = Set("scheduleTopic", "extraScheduleTopic")

  override implicit lazy val system = TestActorSystem(kafkaServer.kafkaPort)
  implicit val conf = SchedulerConfig(ScheduleTopic, 100)
}
