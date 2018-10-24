package com.sky.kms.utils

import java.util.concurrent.CompletionStage

import akka.Done
import akka.kafka.ConsumerMessage
import akka.kafka.ConsumerMessage.{CommittableOffset, GroupTopicPartition, PartitionOffset}

import scala.concurrent.Future
import scala.compat.java8.FutureConverters._

object StubOffset {
  def apply(): CommittableOffset = new CommittableOffset {
    override def partitionOffset: ConsumerMessage.PartitionOffset = PartitionOffset(GroupTopicPartition("", "", 0), 0l)

    override def commitScaladsl(): Future[Done] = Future.successful(Done)

    override def commitJavadsl(): CompletionStage[Done] = Future.successful[Done](Done).toJava
  }
}
