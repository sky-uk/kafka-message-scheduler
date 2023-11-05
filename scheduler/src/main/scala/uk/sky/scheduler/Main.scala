package uk.sky.scheduler

import cats.effect.{Deferred, IO, IOApp}
import cats.syntax.all.*
import fs2.*
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.java.OtelJava
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax.*
import uk.sky.scheduler.config.Config
import uk.sky.scheduler.config.Config.given

object Main extends IOApp.Simple {

  override def run: IO[Unit] =
    for {
      config                 <- ConfigSource.default.loadF[IO, Config]()
      given LoggerFactory[IO] = Slf4jFactory.create[IO]
      _                      <- LoggerFactory[IO].getLogger.info(s"Loaded Config: ${config.show}")
      otel4s                 <- OtelJava.global[IO]
      allowEnqueue           <- Deferred[IO, Unit]
      scheduleQueue          <- ScheduleQueue.live[IO](allowEnqueue)
      eventSubscriber        <- EventSubscriber.kafka[IO](config.scheduler, allowEnqueue).map(EventSubscriber.observed)
      schedulePublisher      <- SchedulePublisher.kafka[IO](config.scheduler, scheduleQueue.queue).pure[IO]
      _                      <- Scheduler[IO, Unit](eventSubscriber, scheduleQueue, schedulePublisher).stream.compile.drain
    } yield ()

}
