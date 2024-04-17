package uk.sky.scheduler.grpc

import cats.Monoid
import cats.effect.Sync
import cats.syntax.all.*
import org.typelevel.otel4s.context.propagation.{TextMapGetter, TextMapUpdater}
import uk.sky.scheduler.otel.Otel

import scala.jdk.CollectionConverters.*

case class Metadata(value: Map[String, String]) {
  def combine(other: Metadata): Metadata = Metadata(this.value ++ other.value)
}

object Metadata {
  val empty: Metadata = Metadata(Map.empty[String, String])

  def one(key: String, value: String): Metadata = Metadata(Map[String, String](key -> value))

  def from[F[_] : Sync : Otel](metadata: io.grpc.Metadata): F[Metadata] =
    metadata
      .keys()
      .asScala
      .toList
      .flatTraverse { k =>
        Sync[F]
          .delay(metadata.get(io.grpc.Metadata.Key.of(k, io.grpc.Metadata.ASCII_STRING_MARSHALLER)))
          .attempt
          .map {
            case Left(t)      => List.empty[(String, String)]
            case Right(value) => List(k -> value)
          }
      }
      .map(ctx => Metadata(ctx.toMap))

  extension (metadata: Metadata) {
    def toGrpcMetadata[F[_] : Sync]: F[io.grpc.Metadata] =
      Sync[F].delay(io.grpc.Metadata()).flatTap { m =>
        metadata.value.toList.traverse { (k, v) =>
          Sync[F]
            .delay(m.put(io.grpc.Metadata.Key.of(k, io.grpc.Metadata.ASCII_STRING_MARSHALLER), v))
            .recoverWith(_ => Sync[F].unit)
        }
      }
  }

  given Monoid[Metadata] = new Monoid[Metadata] {
    override def empty: Metadata                             = Metadata.empty
    override def combine(x: Metadata, y: Metadata): Metadata = x.combine(y)
  }

  given TextMapGetter[Metadata] = new TextMapGetter[Metadata] {
    override def get(carrier: Metadata, key: String): Option[String] = carrier.value.get(key)
    override def keys(carrier: Metadata): Iterable[String]           = carrier.value.keys
  }

  given TextMapUpdater[Metadata] = new TextMapUpdater[Metadata] {
    override def updated(carrier: Metadata, key: String, value: String): Metadata =
      carrier.combine(Metadata.one(key, value))
  }
}
