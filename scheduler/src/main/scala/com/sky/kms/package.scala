package com.sky

import cats.data.Reader

package object kms {

  type Start[T] = Reader[SchedulerApp, T]

  object Start {
    def apply[T](f: SchedulerApp => T): Start[T] = Reader(f)
  }

}
