package com.sky.kms.common

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

trait KafkaIntSpec extends EmbeddedKafka with BeforeAndAfterEach {
  this: Suite =>

  override def beforeEach() {
    kafkaServer.startup()
    super.beforeEach()
  }

  override def afterEach() {
    super.afterEach()
    kafkaServer.close()
  }

}
