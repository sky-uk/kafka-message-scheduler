package uk.sky.scheduler.message

import cats.syntax.all.*
import cats.{Eq, Functor, Show}
import monocle.syntax.all.*

final case class Message[V](key: String, source: String, value: V, metadata: Metadata) {
  def transform[B](f: V => B): Message[B] = this.copy(value = f(this.value))
}

object Message {
  extension [T](message: Message[T]) def expire: Message[T] = message.focus(_.metadata).modify(_.expire)

  given [V : Eq]: Eq[Message[V]] = Eq.by { case Message(key, source, value, metadata) =>
    (key, source, value, metadata)
  }

  given [V : Show]: Show[Message[V]] = Show.show { case Message(key, source, value, metadata) =>
    show"""Message(key=$key, source=$source, value=$value, metadata=$metadata)"""
  }

  given Functor[Message] = new Functor[Message] {
    override def map[A, B](fa: Message[A])(f: A => B): Message[B] = fa.transform(f)
  }
}
