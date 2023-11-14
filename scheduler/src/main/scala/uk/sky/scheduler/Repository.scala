package uk.sky.scheduler

import cats.Monad
import cats.effect.Sync
import cats.effect.std.MapRef
import cats.syntax.all.*
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter

trait Repository[F[_], K, V] {
  def set(key: K, value: V): F[Unit]
  def get(key: K): F[Option[V]]
  def delete(key: K): F[Unit]
}

object Repository {
  def apply[F[_], K, V](mapRef: MapRef[F, K, Option[V]]): Repository[F, K, V] =
    new Repository[F, K, V] {
      override def set(key: K, value: V): F[Unit] = mapRef.setKeyValue(key, value)
      override def get(key: K): F[Option[V]]      = mapRef(key).get
      override def delete(key: K): F[Unit]        = mapRef.unsetKey(key)
    }

  def observed[F[_] : Monad : Meter, K, V](
      mapRef: MapRef[F, K, Option[V]]
  )(name: String): F[Repository[F, K, V]] =
    Meter[F].upDownCounter(s"$name-size").create.map { counter =>
      val totalAttribute  = Attribute("counter.type", "total")
      val setAttribute    = Attribute("counter.type", "set")
      val deleteAttribute = Attribute("counter.type", "delete")

      new Repository[F, K, V] {
        private def getAndSetF(key: K, value: Option[V])(ifPresent: => F[Unit]): F[Unit] =
          mapRef(key).getAndSet(value).flatMap {
            case Some(_) => ifPresent
            case None    => Monad[F].unit
          }

        override def set(key: K, value: V): F[Unit] =
          getAndSetF(key, value.some)(counter.inc(totalAttribute) *> counter.inc(setAttribute))

        override def get(key: K): F[Option[V]] =
          mapRef(key).get

        override def delete(key: K): F[Unit] =
          getAndSetF(key, None)(counter.dec(totalAttribute) *> counter.inc(deleteAttribute))

      }
    }

  def live[F[_] : Sync : Meter, K, V](name: String): F[Repository[F, K, V]] =
    for {
      mapRef <- MapRef.ofScalaConcurrentTrieMap[F, K, V]
      repo   <- Repository.observed(mapRef)(name)
    } yield repo
}
