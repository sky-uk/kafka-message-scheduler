package com.sky.kms.common

import org.scalatest.{BeforeAndAfterAll, Suite}

trait KafkaIntSpec extends EmbeddedKafka with BeforeAndAfterAll {
  this: Suite =>

  override def beforeAll() {
    kafkaServer.startup()
    super.beforeAll()
  }

  override def afterAll() {
    super.afterAll()
    kafkaServer.close()
  }

}
