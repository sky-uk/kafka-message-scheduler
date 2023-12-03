package uk.sky.scheduler

import org.typelevel.ci.CIString
import uk.sky.scheduler.Message.Headers
import uk.sky.scheduler.Message.Headers.*

case class Message[V](key: String, source: String, value: V, headers: Headers)

object Message {

  opaque type Headers = Map[CIString, CIString]

  object Headers {
    extension (headers: Headers) {
      def getHeader(key: String): Option[CIString] =
        headers.get(CIString(key))

      def setHeader(key: String, value: String): Map[CIString, CIString] =
        headers.updated(CIString(key), CIString(value))

      def deleteHeader(key: String): Map[CIString, CIString] =
        headers.removed(CIString(key))
    }

    def fromMap(rawHeaders: Map[String, String]): Headers =
      rawHeaders.map(CIString(_) -> CIString(_))

    final val expiredHeaderKey: String   = "schedule:expired"
    final val expiredHeaderValue: String = "true"
  }

  extension [T](message: Message[T]) {
    def expired: Boolean = message.headers.getHeader(expiredHeaderKey).contains(expiredHeaderValue)
  }
}
