package com.sky.kms.utils

import akka.Done
import akka.kafka.scaladsl.Consumer.Control
import org.apache.kafka.common.{Metric, MetricName}

import scala.concurrent.Future

object StubControl {
  def apply(): Control = new Control {
    override def stop(): Future[Done] = Future.successful(Done)

    override def shutdown(): Future[Done] = Future.successful(Done)

    override def isShutdown: Future[Done] = Future.successful(Done)

    override def metrics: Future[Map[MetricName, Metric]] =
      Future.successful(Map.empty)
  }
}
