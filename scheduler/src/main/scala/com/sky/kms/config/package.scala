package com.sky.kms

import cats.data.Reader

package object config {

  type Configured[T] = Reader[AppConfig, T]

  val Parallelism = 10
}
