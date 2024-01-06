package uk.sky.scheduler.message

import monocle.syntax.all.*

case class Message[V](key: String, source: String, value: V, metadata: Metadata)

object Message {
  extension [T](message: Message[T]) {
    def isExpired: Boolean = message.metadata.isExpired
    def expire: Message[T] = message.focus(_.metadata).modify(_.expire)
  }
}
