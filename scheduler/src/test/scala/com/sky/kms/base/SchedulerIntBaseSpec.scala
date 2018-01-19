package com.sky.kms.base

import com.sky.kms.common.{KafkaIntSpec, TestActorSystem}
import com.sky.kms.config.SchedulerConfig

import scala.concurrent.duration._

abstract class SchedulerIntBaseSpec extends AkkaStreamBaseSpec with KafkaIntSpec {

  override implicit lazy val system = TestActorSystem(kafkaServer.kafkaPort)

  val ScheduleTopic = "scheduleTopic"

  val conf = SchedulerConfig(ScheduleTopic, 10 seconds, 1)

}
