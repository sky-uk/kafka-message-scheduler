package uk.sky.scheduler

import cats.effect.Resource.ExitCase
import cats.effect.{IO, IOApp, Resource}
import cats.syntax.all.*
import fs2.*
import fs2.grpc.syntax.all.*
import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.otel4s.oteljava.OtelJava
import pureconfig.ConfigSource
import pureconfig.module.catseffect.syntax.*
import uk.sky.scheduler.config.Config.given
import uk.sky.scheduler.config.{configShow, Config}
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.grpc.v1
import uk.sky.scheduler.otel.Otel
import uk.sky.scheduler.repository.Repository

import scala.jdk.CollectionConverters.*

object Main extends IOApp.Simple {

  private val appName: String = "kafka-message-scheduler"

  private def kafkaStream(
      config: Config,
      scheduleEventRepository: Repository[IO, String, ScheduleEvent]
  )(using Otel[IO]): Stream[IO, Unit] = {
    val logger = Otel[IO].loggerFactory.getLogger
    for {
      scheduler <- Stream.resource(Scheduler.live[IO](config, scheduleEventRepository))
      message   <- scheduler.stream.onFinalizeCase {
                     case ExitCase.Succeeded  => logger.info("Stream Succeeded")
                     case ExitCase.Errored(e) => logger.error(e)(s"Stream error - ${e.getMessage}")
                     case ExitCase.Canceled   => logger.info("Stream canceled")
                   }
    } yield message
  }

  private def grpcServer(
      config: Config,
      scheduleEventRepository: Repository[IO, String, ScheduleEvent]
  )(using Otel[IO]): Resource[IO, Server] =
    for {
      reflection  <- IO.delay(ProtoReflectionService.newInstance().bindService()).toResource
      schedulerV1 <- v1.Scheduler.live[IO](scheduleEventRepository)
      server      <- NettyServerBuilder
                       .forPort(config.scheduler.grpc.port)
                       .addServices(List(reflection, schedulerV1).asJava)
                       .resource[IO]
                       .evalMap(server => IO.delay(server.start()))
    } yield server

  override def run: IO[Unit] = {
    val resource =
      for {
        config                  <- ConfigSource.default.loadF[IO, Config]().toResource
        otel4s                  <- OtelJava.global[IO].toResource
        meter                   <- otel4s.meterProvider.get(appName).toResource
        tracer                  <- otel4s.tracerProvider.get(appName).toResource
        given Otel[IO]           = Otel.from(Slf4jFactory.create[IO], tracer, meter)
        logger                   = Otel[IO].loggerFactory.getLogger
        _                       <- logger.info(s"Loaded Config: ${config.show}").toResource
        scheduleEventRepository <- Repository.live[IO, String, ScheduleEvent]("schedule-events").toResource
        server                  <- grpcServer(config, scheduleEventRepository)
        stream                  <- kafkaStream(config, scheduleEventRepository).pure[IO].toResource.evalMap(_.compile.drain)
      } yield (server, stream)

    resource.useForever
  }

}
