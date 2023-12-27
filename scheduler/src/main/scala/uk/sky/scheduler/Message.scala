package uk.sky.scheduler

import cats.{Eq, Monoid}
import monocle.syntax.all.*
import org.typelevel.ci.CIString
import uk.sky.scheduler.Message.Headers
import uk.sky.scheduler.Message.Headers.*

import scala.annotation.targetName

case class Message[V](key: String, source: String, value: V, headers: Headers)

object Message {

  opaque type Headers = Map[CIString, CIString]

  object Headers {
    extension (headers: Headers) {
      def getHeader(key: String): Option[CIString] =
        headers.get(CIString(key))

      def setHeader(key: String, value: String): Headers =
        headers.updated(CIString(key), CIString(value))

      def deleteHeader(key: String): Headers =
        headers.removed(CIString(key))

      @targetName("combine")
      def ++(other: Headers): Headers =
        headers ++ other
    }

    def apply(rawHeaders: Map[String, String]): Headers =
      rawHeaders.map(CIString(_) -> CIString(_))

    final val expiredHeaderKey: String   = "schedule:expired"
    final val expiredHeaderValue: String = "true"

    final val empty: Headers = Map.empty[CIString, CIString]

    given Eq[Headers] = Eq.fromUniversalEquals

    given Monoid[Headers] = new Monoid[Headers] {
      override def empty: Headers = Headers.empty

      override def combine(x: Headers, y: Headers): Headers = x ++ y
    }
  }

  extension [T](message: Message[T]) {
    def expired: Boolean = message.headers.getHeader(expiredHeaderKey).contains(CIString(expiredHeaderValue))

    def expire: Message[T] = message.focus(_.headers).modify(_.setHeader(expiredHeaderKey, expiredHeaderValue))
  }
}
