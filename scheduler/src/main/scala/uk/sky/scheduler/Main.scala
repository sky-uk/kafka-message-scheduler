package uk.sky.scheduler

import cats.effect.Resource.ExitCase
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all.*
import fs2.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.Otel4s
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.oteljava.OtelJava
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax.*
import uk.sky.scheduler.config.Config.given
import uk.sky.scheduler.config.{configShow, Config}

object Main extends IOApp.Simple {

  private given LoggerFactory[IO] = Slf4jFactory.create[IO]

  private val logger = LoggerFactory.getLogger[IO]

  private val otel4s: Resource[IO, Otel4s[IO]] =
    Resource.eval(OtelJava.global[IO])

  override def run: IO[Unit] = {
    for {
      config          <- Stream.eval(ConfigSource.default.at(Config.metadata.appName).loadF[IO, Config]())
      otel4s          <- Stream.resource(otel4s)
      given Meter[IO] <- Stream.eval(otel4s.meterProvider.get(Config.metadata.appName))
      _               <- Stream.eval(logger.info(s"Running ${Config.metadata.appName} with version ${Config.metadata.version}"))
      _               <- Stream.eval(logger.info(s"Loaded Config: ${config.show}"))
      scheduler       <- Stream.resource(Scheduler.live[IO](config))
      message         <- scheduler.stream
    } yield message
  }.onFinalizeCase {
    case ExitCase.Succeeded  => logger.info("Stream Succeeded")
    case ExitCase.Errored(e) => logger.error(e)(s"Stream error - ${e.getMessage}")
    case ExitCase.Canceled   => logger.info("Stream canceled")
  }.compile.drain

}
