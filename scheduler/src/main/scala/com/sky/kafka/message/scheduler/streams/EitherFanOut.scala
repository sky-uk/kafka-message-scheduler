package com.sky.kafka.message.scheduler.streams

import akka.stream.FanOutShape2
import akka.stream.stage.{GraphStage, InHandler, OutHandler}

//Taken from: https://stackoverflow.com/a/38445121/8424807
class EitherFanOut[L, R] extends GraphStage[FanOutShape2[Either[L, R], L, R]] {
  import akka.stream.Attributes
  import akka.stream.stage.GraphStageLogic

  override val shape: FanOutShape2[Either[L, R], L, R] = new FanOutShape2[Either[L, R], L, R]("EitherFanOut")

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    var out0demand = false
    var out1demand = false

    setHandler(shape.in, new InHandler {
      override def onPush(): Unit = {

        if (out0demand && out1demand) {
          grab(shape.in) match {
            case Left(l) =>
              out0demand = false
              push(shape.out0, l)
            case Right(r) =>
              out1demand = false
              push(shape.out1, r)
          }
        }
      }
    })

    setHandler(shape.out0, new OutHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPull(): Unit = {
        if (!out0demand) {
          out0demand = true
        }

        if (out0demand && out1demand) {
          pull(shape.in)
        }
      }
    })

    setHandler(shape.out1, new OutHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPull(): Unit = {
        if (!out1demand) {
          out1demand = true
        }

        if (out0demand && out1demand) {
          pull(shape.in)
        }
      }
    })
  }
}