package uk.sky.scheduler.message

import cats.{Eq, Monoid, Show}
import org.typelevel.ci
import org.typelevel.ci.CIString

opaque type Metadata = Map[CIString, String]

object Metadata {
  def apply(value: Iterable[(CIString, String)]): Metadata = value.toMap

  extension (metadata: Metadata) {
    inline def value: Map[CIString, String]                    = metadata
    inline def toMap: Map[String, String]                      = metadata.map(_.toString -> _)
    inline def combine(other: Metadata): Metadata              = metadata.concat(other)
    inline infix def ++(other: Metadata): Metadata             = combine(other)
    inline def updated(key: CIString, value: String): Metadata = metadata.updated(key, value)
    inline infix def +(kv: (CIString, String)): Metadata       = updated(kv._1, kv._2)

    inline def isExpired: Boolean = metadata.get(expiredKey).exists(_.equalsIgnoreCase(expiredValue))
  }

  val expiredKey   = CIString("schedule:expired")
  val expiredValue = "true"

  val empty: Metadata = Map.empty[CIString, String]

  given Monoid[Metadata] = new Monoid[Metadata] {
    override def empty: Metadata                             = Metadata.empty
    override def combine(x: Metadata, y: Metadata): Metadata = x.combine(y)
  }

  given Show[Metadata] = Show.catsShowForMap[CIString, String]
  given Eq[Metadata]   = Eq.catsKernelEqForMap[CIString, String]
}
