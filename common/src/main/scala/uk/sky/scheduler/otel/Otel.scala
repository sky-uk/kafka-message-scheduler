package uk.sky.scheduler.otel

import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.Tracer

trait Otel[F[_]] {
  def loggerFactory: LoggerFactory[F]
  def tracer: Tracer[F]
  def meter: Meter[F]
}

object Otel {
  def apply[F[_]](using otel: Otel[F]): Otel[F] = otel

  def from[F[_]](lf: LoggerFactory[F], t: Tracer[F], m: Meter[F]): Otel[F] = new Otel[F] {
    override def loggerFactory: LoggerFactory[F] = lf
    override def tracer: Tracer[F]               = t
    override def meter: Meter[F]                 = m
  }

  given [F[_]](using Otel[F]): LoggerFactory[F] = Otel[F].loggerFactory
  given [F[_]](using Otel[F]): Tracer[F]        = Otel[F].tracer
  given [F[_]](using Otel[F]): Meter[F]         = Otel[F].meter
}
