package uk.sky.scheduler

import cats.effect.Resource.ExitCase
import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import fs2.*
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.oteljava.OtelJava
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax.*
import uk.sky.scheduler.config.Config.given
import uk.sky.scheduler.config.{configShow, Config}
import uk.sky.scheduler.otel.Otel

object Main extends IOApp.Simple {

  private val appName: String = "kafka-message-scheduler"

  private def kafkaStream(using Otel[IO]): Stream[IO, Unit] = {
    val logger = Otel[IO].loggerFactory.getLogger
    for {
      config    <- Stream.eval(ConfigSource.default.loadF[IO, Config]())
      otel4s    <- Stream.eval(OtelJava.global[IO])
      _         <- Stream.eval(logger.info(s"Loaded Config: ${config.show}"))
      scheduler <- Stream.resource(Scheduler.live[IO](config))
      message   <- scheduler.stream.onFinalizeCase {
                     case ExitCase.Succeeded  => logger.info("Stream Succeeded")
                     case ExitCase.Errored(e) => logger.error(e)(s"Stream error - ${e.getMessage}")
                     case ExitCase.Canceled   => logger.info("Stream canceled")
                   }
    } yield message
  }

  override def run: IO[Unit] = {
    val r =
      for {
        config        <- ConfigSource.default.loadF[IO, Config]()
        otel4s        <- OtelJava.global[IO]
        meter         <- otel4s.meterProvider.get(appName)
        tracer        <- otel4s.tracerProvider.get(appName)
        given Otel[IO] = Otel.from(Slf4jFactory.create[IO], tracer, meter)
        logger         = Otel[IO].loggerFactory.getLogger
        _             <- logger.info(s"Loaded Config: ${config.show}")
      } yield kafkaStream

    r.flatMap(_.compile.drain)
  }

}
