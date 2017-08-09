package com.sky.kms.common

import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig

object TestActorSystem {

  val config =
    s"""
       |akka {
       | kafka {
       |  consumer {
       |    kafka-clients {
       |      bootstrap.servers = "${EmbeddedKafka.bootstrapServer}"
       |      ${ConsumerConfig.AUTO_OFFSET_RESET_CONFIG} = "earliest"
       |    }
       |  }
       |
       |  producer.kafka-clients.bootstrap.servers = "${EmbeddedKafka.bootstrapServer}"
       | }
       |}
    """.stripMargin


  def apply(): ActorSystem =
    ActorSystem(
      name = s"test-actor-system-${UUID.randomUUID().toString}",
      config = ConfigFactory.parseString(config).withFallback(ConfigFactory.load())
    )
}
