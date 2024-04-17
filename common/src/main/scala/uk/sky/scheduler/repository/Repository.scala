package uk.sky.scheduler.repository

import cats.effect.Sync
import cats.effect.std.MapRef
import cats.syntax.all.*
import cats.{Functor, Monad, Parallel}
import mouse.all.*
import org.typelevel.otel4s.Attribute
import uk.sky.scheduler.otel.Otel

trait Repository[F[_], K, V] {
  def set(key: K, value: V): F[Unit]
  def get(key: K): F[Option[V]]
  def delete(key: K): F[Unit]
}

object Repository {
  private class RepositoryImpl[F[_], K, V](mapRef: MapRef[F, K, Option[V]]) {
    def set(key: K, value: V): F[Option[V]] = mapRef(key).getAndSet(value.some)
    def get(key: K): F[Option[V]]           = mapRef(key).get
    def delete(key: K): F[Option[V]]        = mapRef(key).getAndSet(None)
  }

  def apply[F[_] : Functor, K, V](mapRef: MapRef[F, K, Option[V]]): Repository[F, K, V] =
    new Repository[F, K, V] {
      private val underlying = RepositoryImpl(mapRef)

      override def set(key: K, value: V): F[Unit] = underlying.set(key, value).void
      override def get(key: K): F[Option[V]]      = underlying.get(key)
      override def delete(key: K): F[Unit]        = underlying.delete(key).void
    }

  private val totalAttribute  = Attribute("counter.type", "total")
  private val setAttribute    = Attribute("counter.type", "set")
  private val deleteAttribute = Attribute("counter.type", "delete")

  def observed[F[_] : Monad : Parallel : Otel, K, V](name: String)(
      mapRef: MapRef[F, K, Option[V]]
  ): F[Repository[F, K, V]] =
    Otel[F].meter.upDownCounter[Long](s"$name-repository-size").create.map { counter =>
      new Repository[F, K, V] {
        private val underlying = RepositoryImpl(mapRef)

        override def set(key: K, value: V): F[Unit] =
          underlying
            .set(key, value)
            .foldF(counter.inc(totalAttribute) &> counter.inc(setAttribute))(_ => Monad[F].unit)

        override def get(key: K): F[Option[V]] =
          underlying.get(key)

        override def delete(key: K): F[Unit] =
          underlying
            .delete(key)
            .foldF(Monad[F].unit)(_ => counter.dec(totalAttribute) &> counter.inc(deleteAttribute))
      }
    }

  def live[F[_] : Sync : Parallel : Otel, K, V](name: String): F[Repository[F, K, V]] =
    MapRef.ofScalaConcurrentTrieMap[F, K, V].flatMap(observed(name))
}
