package com.sky.kms

import akka.util.Timeout
import cats.data.Reader

import scala.concurrent.duration._

package object config {

  type Configured[T] = Reader[AppConfig, T]

  implicit val timeout = Timeout(2.minutes)

  val Parallelism = 5
}
