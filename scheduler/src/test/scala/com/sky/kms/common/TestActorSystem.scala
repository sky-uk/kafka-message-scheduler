package com.sky.kms.common

import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig

object TestActorSystem {

  private def config(kafkaPort: Int, terminateActorSystem: Boolean): String =
    s"""
       |akka {
       | coordinated-shutdown {
       |  terminate-actor-system = $terminateActorSystem
       |  run-by-jvm-shutdown-hook = off
       | }
       |
       | kafka {
       |  consumer {
       |    max-wakeups = 2
       |    wakeup-timeout = 2 seconds
       |    kafka-clients {
       |      enable.auto.commit = false
       |      bootstrap.servers = "localhost:$kafkaPort"
       |      ${ConsumerConfig.AUTO_OFFSET_RESET_CONFIG} = "earliest"
       |    }
       |  }
       |
       |  producer.kafka-clients.bootstrap.servers = "localhost:$kafkaPort"
       | }
       |}
    """.stripMargin


  def apply(kafkaPort: Int = 9092, terminateActorSystem: Boolean = false): ActorSystem =
    ActorSystem(
      name = s"test-actor-system-${UUID.randomUUID().toString}",
      config = ConfigFactory.parseString(config(kafkaPort, terminateActorSystem)).withFallback(ConfigFactory.load())
    )
}
