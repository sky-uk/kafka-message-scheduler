package uk.sky.scheduler.message

import cats.syntax.all.*
import cats.{Eq, Functor}
import monocle.syntax.all.*

case class Message[V](key: String, source: String, value: V, metadata: Metadata) {
  def map[B](f: V => B): Message[B] = this.copy(value = f(this.value))
}

object Message {
  extension [T](message: Message[T]) {
    def isExpired: Boolean = message.metadata.isExpired
    def expire: Message[T] = message.focus(_.metadata).modify(_.expire)
  }

  given [V : Eq]: Eq[Message[V]] = (x, y) =>
    x.key === y.key &&
      x.source === y.source &&
      x.value === y.value &&
      x.metadata === y.metadata

  given Functor[Message] = new Functor[Message] {
    override def map[A, B](fa: Message[A])(f: A => B): Message[B] =
      fa.map(f)
  }
}
