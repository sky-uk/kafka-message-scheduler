package uk.sky.scheduler

import cats.Monad
import cats.data.OptionT
import cats.effect.Sync
import cats.effect.std.MapRef
import cats.syntax.all.*
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
      new Repository[F, K, V] {
        override def set(key: K, value: V): F[Unit] = {
          for {
            _ <- OptionT(mapRef(key).getAndSet(value.some))
            _ <- OptionT.liftF(counter.inc())
          } yield ()
        }.value.void

        override def get(key: K): F[Option[V]] = mapRef(key).get

        override def delete(key: K): F[Unit] = {
          for {
            _ <- OptionT(mapRef(key).getAndSet(None))
            _ <- OptionT.liftF(counter.dec())
          } yield ()
        }.value.void
      }
    }

  def live[F[_] : Sync : Meter, K, V](name: String): F[Repository[F, K, V]] =
    for {
      mapRef <- MapRef.ofScalaConcurrentTrieMap[F, K, V]
      repo   <- Repository.observed(mapRef)(name)
    } yield repo
}
