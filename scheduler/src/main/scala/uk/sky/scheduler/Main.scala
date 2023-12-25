package uk.sky.scheduler

import cats.effect.Resource.ExitCase
import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import fs2.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.java.OtelJava
import org.typelevel.otel4s.metrics.Meter
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax.*
import uk.sky.scheduler.config.Config.given
import uk.sky.scheduler.config.{configShow, Config}

object Main extends IOApp.Simple {

  val appName             = "kafka-message-scheduler"
  given LoggerFactory[IO] = Slf4jFactory.create[IO]
  val logger              = LoggerFactory.getLogger[IO]

  override def run: IO[Unit] = {
    for {
      config          <- Stream.eval(ConfigSource.default.loadF[IO, Config]())
      otel4s          <- Stream.eval(OtelJava.global[IO])
      given Meter[IO] <- Stream.eval(otel4s.meterProvider.get(appName))
      _               <- Stream.eval(logger.info(s"Loaded Config: ${config.show}"))
      scheduler       <- Stream.resource(Scheduler.live[IO](config))
      message         <- scheduler.stream.onFinalizeCase {
                           case ExitCase.Succeeded  => logger.info("Stream Succeeded")
                           case ExitCase.Errored(e) => logger.error(e)(s"Stream error - ${e.getMessage}")
                           case ExitCase.Canceled   => logger.info("Stream canceled")
                         }
    } yield message
  }.compile.drain

}
