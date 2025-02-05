package uk.sky.scheduler

import cats.data.Reader
import cats.effect.*
import fs2.Stream
import org.typelevel
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax.*
import uk.sky.scheduler.config.Config

object Main extends IOApp.Simple {

  def stream: Reader[Config, Stream[IO, Unit]] =
    for {
      scheduler <- Scheduler.live[IO]
      stream    <- Reader[Config, Stream[IO, Scheduler[IO, Unit]]](_ => Stream.resource(scheduler))
    } yield stream.flatMap(_.stream)

  override def run: IO[Unit] = for {
    given LoggerFactory[IO] <- IO(Slf4jFactory.create[IO])
    logger                  <- LoggerFactory[IO].create
    config                  <- ConfigSource.default.loadF[IO, Config]()
    _                       <- logger.info(s"Running ${Config.metadata.appName} with version ${Config.metadata.version}")
    _                       <- logger.info(s"Loaded config: $config")
    _                       <- stream(config).compile.drain
  } yield ()

}
