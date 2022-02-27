package com.sky.kms.utils

import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.concurrent.duration._

object TestActorSystem {

  private def config(kafkaPort: Int, terminateActorSystem: Boolean, akkaExpectDuration: Duration): String =
    s"""
       |akka {
       | test.single-expect-default = $akkaExpectDuration
       | coordinated-shutdown {
       |  terminate-actor-system = $terminateActorSystem
       |  run-by-actor-system-terminate = off
       |  run-by-jvm-shutdown-hook = off
       | }
       |
       | kafka {
       |  consumer {
       |    stop-timeout = 1s
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

  def apply(
      kafkaPort: Int = 9092,
      terminateActorSystem: Boolean = false,
      akkaExpectDuration: Duration = 3.seconds
  ): ActorSystem =
    ActorSystem(
      name = s"test-actor-system-${UUID.randomUUID().toString}",
      config = ConfigFactory
        .parseString(config(kafkaPort, terminateActorSystem, akkaExpectDuration))
        .withFallback(ConfigFactory.load())
    )
}
