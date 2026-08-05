package uk.sky.scheduler

import cats.effect.Sync
import cats.effect.std.MapRef
import cats.syntax.all.*
import cats.{Monad, Parallel}
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter

import scala.collection.concurrent.TrieMap

trait Repository[F[_], K, V] {
  def set(key: K, value: V): F[Unit]
  def get(key: K): F[Option[V]]
  def delete(key: K): F[Unit]
  def getAll: F[Map[K, V]]
  def size: F[Int]
}

object Repository {
  private class TrieMapRepository[F[_] : Sync, K, V](trieMap: TrieMap[K, V]) {
    private val mapRef = MapRef.fromScalaConcurrentMap(trieMap)

    def set(key: K, value: V): F[Option[V]] = mapRef(key).getAndSet(value.some)
    def get(key: K): F[Option[V]]           = mapRef(key).get
    def delete(key: K): F[Option[V]]        = mapRef(key).getAndSet(None)
    def getAll: F[Map[K, V]]                = Sync[F].delay(trieMap.toMap)
    def size: F[Int]                        = Sync[F].delay(trieMap.size)
  }

  private object RepositoryAttribute {
    private def attribute(value: String) = Attribute("counter.type", value)

    val Total: Attribute[String]  = attribute("total")
    val Set: Attribute[String]    = attribute("set")
    val Delete: Attribute[String] = attribute("delete")
    val Hit: Attribute[String]    = attribute("hit")
    val Miss: Attribute[String]   = attribute("miss")
  }

  def ofScalaConcurrentTrieMap[F[_] : Sync : Parallel : Meter, K, V](name: String): F[Repository[F, K, V]] =
    for {
      trieMap <- Sync[F].delay(TrieMap.empty[K, V])
      counter <- Meter[F].upDownCounter[Long](s"$name-repository-size").create
    } yield {
      new Repository[F, K, V] {
        private val underlying = TrieMapRepository(trieMap)

        override def set(key: K, value: V): F[Unit] =
          underlying
            .set(key, value)
            .flatMap {
              case Some(_) => counter.inc(RepositoryAttribute.Set)
              case None    => counter.inc(RepositoryAttribute.Total) &> counter.inc(RepositoryAttribute.Set)
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
              case Some(_) => counter.dec(RepositoryAttribute.Total) &> counter.inc(RepositoryAttribute.Delete)
              case None    => Monad[F].unit
            }

        override def getAll: F[Map[K, V]] = underlying.getAll
        override def size: F[Int]         = underlying.size
      }
    }
}
