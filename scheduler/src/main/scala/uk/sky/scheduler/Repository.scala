package uk.sky.scheduler

import cats.effect.std.MapRef
import cats.effect.{Async, Sync}
import cats.syntax.all.*
import cats.{Functor, Monad, Parallel}
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter

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

  private object RepositoryAttribute {
    private def attribute(value: String) = Attribute("counter.type", value)

    val Total: Attribute[String]  = attribute("total")
    val Set: Attribute[String]    = attribute("set")
    val Delete: Attribute[String] = attribute("delete")
    val Hit: Attribute[String]    = attribute("hit")
    val Miss: Attribute[String]   = attribute("miss")
  }

  def observed[F[_] : Monad : Parallel : Meter, K, V](name: String)(
      mapRef: MapRef[F, K, Option[V]]
  ): F[Repository[F, K, V]] =
    Meter[F].upDownCounter[Long](s"$name-repository-size").create.map { counter =>
      new Repository[F, K, V] {
        private val underlying = RepositoryImpl(mapRef)

        override def set(key: K, value: V): F[Unit] =
          underlying
            .set(key, value)
            .flatMap {
              case Some(_) => counter.inc(RepositoryAttribute.Total) &> counter.inc(RepositoryAttribute.Set)
              case None    => Monad[F].unit
            }

        override def get(key: K): F[Option[V]] =
          underlying
            .get(key)
            .flatTap {
              case Some(_) => counter.inc(RepositoryAttribute.Hit)
              case None    => counter.inc(RepositoryAttribute.Miss)
            }

        override def delete(key: K): F[Unit] =
          underlying
            .delete(key)
            .flatMap {
              case Some(_) => Monad[F].unit
              case None    => counter.dec(RepositoryAttribute.Total) &> counter.inc(RepositoryAttribute.Delete)
            }
      }
    }

  def ofScalaConcurrentTrieMap[F[_] : Sync : Parallel : Meter, K, V](name: String): F[Repository[F, K, V]] =
    MapRef.ofScalaConcurrentTrieMap[F, K, V].flatMap(observed(name))

  def ofConcurrentHashMap[F[_] : Sync : Parallel : Meter, K, V](name: String): F[Repository[F, K, V]] =
    MapRef.ofConcurrentHashMap[F, K, V]().flatMap(observed(name))

  def ofShardedImmutableMap[F[_] : Async : Parallel : Meter, K, V](name: String): F[Repository[F, K, V]] =
    MapRef.ofShardedImmutableMap[F, K, V](Runtime.getRuntime.availableProcessors()).flatMap(observed(name))
}
