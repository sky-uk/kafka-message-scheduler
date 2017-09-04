package com.sky.kms.unit

import akka.stream.scaladsl._
import com.sky.kms.base.AkkaStreamBaseSpec
import com.sky.kms.streams.PartitionedSink

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class PartitionedSinkSpec extends AkkaStreamBaseSpec {

  implicit val leftSink: Sink[Int, Future[Seq[Int]]] =
    Flow[Int].toMat(Sink.seq)(Keep.right)

  val rightSink: Sink[String, Future[Int]] =
    Flow[String].map(_.length).toMat(Sink.fold(0)(_ + _))(Keep.right)

  val source = Source(List(Right("test"), Left(5), Right("someString")))

  "withRight" should {
    "emit Right to rightSink and Left to leftSink" in {

      val (leftFuture, rightFuture) = source.runWith(PartitionedSink.withRight(rightSink))

      Await.result(leftFuture, Duration.Inf) shouldBe List(5)
      Await.result(rightFuture, Duration.Inf) shouldBe 14
    }
  }

}
