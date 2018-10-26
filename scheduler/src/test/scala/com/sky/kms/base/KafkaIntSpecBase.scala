package com.sky.kms.base

import com.sky.kms.utils.RandomPort.randomPort
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.scalatest.WordSpecLike

trait KafkaIntSpecBase extends EmbeddedKafka with WordSpecLike {
  implicit lazy val kafkaConfig =
    EmbeddedKafkaConfig(kafkaPort = randomPort(), zooKeeperPort = randomPort())
}
