package com.sky.kms.common

import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig

object TestActorSystem {

  def config(kafkaPort: Int): String =
    s"""
       |akka {
       | coordinated-shutdown {
       |  terminate-actor-system = off
       |  run-by-jvm-shutdown-hook = off
       | }
       |
       | kafka {
       |  consumer {
       |    kafka-clients {
       |      bootstrap.servers = "localhost:$kafkaPort"
       |      ${ConsumerConfig.AUTO_OFFSET_RESET_CONFIG} = "earliest"
       |    }
       |  }
       |
       |  producer.kafka-clients.bootstrap.servers = "localhost:$kafkaPort"
       | }
       |}
    """.stripMargin


  def apply(kafkaPort: Int = 9092): ActorSystem =
    ActorSystem(
      name = s"test-actor-system-${UUID.randomUUID().toString}",
      config = ConfigFactory.parseString(config(kafkaPort)).withFallback(ConfigFactory.load())
    )
}
