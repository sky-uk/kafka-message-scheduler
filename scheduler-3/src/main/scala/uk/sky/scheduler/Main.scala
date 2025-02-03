package uk.sky.scheduler

import cats.effect.*
import fs2.Stream
import org.typelevel
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax.*
import uk.sky.scheduler.config.Config

object Main extends IOApp.Simple {

  def stream[IO[_] : Concurrent](config: Config): Stream[IO, Unit] =
    for {
      scheduler <- Stream.resource(Scheduler.live[IO](config))
      message   <- scheduler.stream
    } yield message

  override def run: IO[Unit] = for {
    given LoggerFactory[IO] <- IO(Slf4jFactory.create[IO])
    logger                  <- LoggerFactory[IO].create
    config                  <- ConfigSource.default.at(Config.metadata.appName).loadF[IO, Config]()
    _                       <- logger.info(s"Running ${Config.metadata.appName} with version ${Config.metadata.version}")
    _                       <- logger.info(s"Loaded config: $config")
    _                       <- stream[IO](config).compile.drain
  } yield ()

}
