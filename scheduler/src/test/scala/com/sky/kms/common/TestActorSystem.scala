package com.sky.kms.common

import java.util.UUID

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig

import scala.concurrent.duration._

object TestActorSystem {

  private def config(kafkaPort: Int, terminateActorSystem: Boolean, maxWakeups: Int, wakeupTimeout: Duration, akkaExpectDuration: Duration): String =
    s"""
       |akka {
       | test.single-expect-default = $akkaExpectDuration
       | coordinated-shutdown {
       |  terminate-actor-system = $terminateActorSystem
       |  run-by-jvm-shutdown-hook = off
       | }
       |
       | kafka {
       |  consumer {
       |    max-wakeups = $maxWakeups
       |    wakeup-timeout = $wakeupTimeout
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


  def apply(kafkaPort: Int = 9092, terminateActorSystem: Boolean = false, maxWakeups: Int = 2, wakeupTimeout: Duration = 2.seconds, akkaExpectDuration: Duration = 3.seconds): ActorSystem =
    ActorSystem(
      name = s"test-actor-system-${UUID.randomUUID().toString}",
      config = ConfigFactory.parseString(config(kafkaPort, terminateActorSystem, maxWakeups, wakeupTimeout, akkaExpectDuration)).withFallback(ConfigFactory.load())
    )
}
