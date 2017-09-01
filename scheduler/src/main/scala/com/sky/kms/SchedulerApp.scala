package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.Reader
import cats.implicits.catsStdInstancesForFuture
import com.sky.kms.config.AppConfig
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import kamon.Kamon

import scala.concurrent.ExecutionContext

case class SchedulerApp(scheduleReader: ScheduleReader, scheduledMessagePublisher: ScheduledMessagePublisher)

object SchedulerApp {

  case class Running(runningReader: ScheduleReader.Mat, runningPublisher: ScheduledMessagePublisher.Mat)

  def reader(implicit system: ActorSystem): Reader[AppConfig, SchedulerApp] =
    for {
      scheduleReader <- ScheduleReader.reader
      publisher <- ScheduledMessagePublisher.reader
    } yield SchedulerApp(scheduleReader, publisher)

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Reader[SchedulerApp, Running] = {
    Kamon.start()
    for {
      runningPublisher <- ScheduledMessagePublisher.run
      runningReader <- ScheduleReader.run(runningPublisher)
    } yield Running(runningReader, runningPublisher)
  }

  def stop(implicit ec: ExecutionContext): Stop[Unit] =
    for {
      _ <- ScheduleReader.stop
      _ <- ScheduledMessagePublisher.stop
      _ <- AkkaComponents.stop
    } yield Kamon.shutdown()
}
