package com.sky.kafka.message.scheduler.streams

import akka.stream.SinkShape
import akka.stream.scaladsl.{GraphDSL, Sink}

object PartitionedSink {

  def from[A, AMat, B, BMat](rightSink: Sink[B, BMat])(implicit leftSink: Sink[A, AMat]): Sink[Either[A, B], (AMat, BMat)] =
    Sink.fromGraph(GraphDSL.create(leftSink, rightSink)((_, _)) { implicit b =>
      (left, right) =>
        import GraphDSL.Implicits._

        val eitherFanOut = b.add(new EitherFanOut[A, B])

        eitherFanOut.out0 ~> left.in
        eitherFanOut.out1 ~> right.in

        SinkShape(eitherFanOut.in)
    })
}
