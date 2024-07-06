package uk.sky.scheduler.message

import cats.syntax.all.*
import cats.{Eq, Monoid, Show}
import org.typelevel.ci
import org.typelevel.ci.CIString

final case class Metadata(value: Map[CIString, String]) {

  def map(f: Map[CIString, String] => Map[CIString, String]): Metadata =
    this.copy(value = f(this.value))

  def combine(other: Metadata): Metadata = map(_.concat(other.value))

  def get(key: String): Option[String] = value.get(CIString(key))

  def set(key: String, value: String): Metadata = map(_.updated(CIString(key), value))

  def remove(key: String): Metadata = map(_.removed(CIString(key)))

  def exists(key: String, value: String): Boolean = get(key).exists(_.equalsIgnoreCase(value))
}

object Metadata {
  def fromMap(metadata: Map[String, String]): Metadata =
    Metadata(metadata.map(CIString(_) -> _))

  extension (metadata: Metadata) {
    def isExpired: Boolean = metadata.exists(expiredKey, expiredValue)
    def expire: Metadata   = metadata.set(expiredKey, expiredValue)
  }

  inline val expiredKey   = "schedule:expired"
  inline val expiredValue = "true"

  val empty: Metadata = Metadata(Map.empty[CIString, String])

  given Eq[Metadata] = Eq.by[Metadata, Map[CIString, String]](_.value)

  given Show[Metadata] = _.value.show

  given Monoid[Metadata] = new Monoid[Metadata] {
    override def empty: Metadata = Metadata.empty

    override def combine(x: Metadata, y: Metadata): Metadata = x.combine(y)
  }
}
