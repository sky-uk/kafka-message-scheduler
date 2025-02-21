package uk.sky.scheduler.message

import cats.syntax.all.*
import cats.{Eq, Monoid, Show}
import org.typelevel.ci.CIString

opaque type Metadata = Map[CIString, String]

object Metadata {
  def apply(value: Iterable[(CIString, String)]): Metadata = value match {
    case map: Map[CIString, String] => map
    case other                      => other.toMap
  }

  val empty: Metadata = Map.empty[CIString, String]

  extension (metadata: Metadata) {
    inline def value: Map[CIString, String]                                           = metadata
    inline def toMap: Map[String, String]                                             = metadata.map(_.toString -> _)
    inline def transform(f: Map[CIString, String] => Map[CIString, String]): Metadata = f(metadata)
    inline def combine(other: Metadata): Metadata                                     = metadata.concat(other)

    inline def isExpired: Boolean = metadata.get(expiredKey).exists(_.equalsIgnoreCase(expiredValue))
    inline def expire: Metadata   = metadata + (expiredKey -> expiredValue)
  }

  given Monoid[Metadata] = new Monoid[Metadata] {
    override def empty: Metadata                             = Metadata.empty
    override def combine(x: Metadata, y: Metadata): Metadata = x.combine(y)
  }

  given Show[Metadata] =
    _.map((k, v) => show"$k: $v")
      .mkString("Metadata(", ", ", ")")

  given Eq[Metadata] = Eq.catsKernelEqForMap[CIString, String]

  val expiredKey   = CIString("schedule:expired")
  val expiredValue = "true"
}
