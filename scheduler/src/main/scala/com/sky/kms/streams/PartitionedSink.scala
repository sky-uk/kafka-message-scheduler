package com.sky.kms.streams

import akka.stream.SinkShape
import akka.stream.contrib.PartitionWith
import akka.stream.scaladsl.{GraphDSL, Sink}

object PartitionedSink {

  def withRight[A, AMat, B, BMat](rightSink: Sink[B, BMat])(implicit leftSink: Sink[A, AMat]): Sink[Either[A, B], (AMat, BMat)] =
    Sink.fromGraph(GraphDSL.create(leftSink, rightSink)((_, _)) { implicit b =>
      (left, right) =>
        import GraphDSL.Implicits._

        val partition = b.add(PartitionWith[Either[A, B], A, B](identity))

        partition.out0 ~> left.in
        partition.out1 ~> right.in

        SinkShape(partition.in)
    })
}
