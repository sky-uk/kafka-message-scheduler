package uk.sky.scheduler

import cats.effect.*
import cats.effect.kernel.Resource.ExitCase
import cats.syntax.all.*
import org.typelevel
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.oteljava.OtelJava
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax.*
import uk.sky.scheduler.config.Config

object Main extends IOApp.Simple {

  override def run: IO[Unit] = for {
    given LoggerFactory[IO] <- IO(Slf4jFactory.create[IO])
    logger                  <- LoggerFactory[IO].create
    otel4s                  <- OtelJava.global[IO]
    given Meter[IO]         <- otel4s.meterProvider.get(Config.metadata.appName)
    config                  <- ConfigSource.default.at(Config.metadata.appName).loadF[IO, Config]()
    _                       <- logger.info(show"Running ${Config.metadata.appName} with version ${Config.metadata.version}")
    stream                  <- Scheduler.live[IO].apply(config.kafka).map(_.stream)
    _                       <- stream
                                 .onFinalizeCase[IO] {
                                   case ExitCase.Succeeded  => logger.info("Stream Succeeded")
                                   case ExitCase.Errored(e) => logger.error(e)(s"Stream error - ${e.getMessage}")
                                   case ExitCase.Canceled   => logger.info("Stream canceled")
                                 }
                                 .compile
                                 .drain
  } yield ()

}
