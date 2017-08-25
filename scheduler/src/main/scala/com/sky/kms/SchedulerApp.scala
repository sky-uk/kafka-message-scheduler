package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.data.Reader
import com.sky.kms.config.{AppConfig, ShutdownTimeout}
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import kamon.Kamon

case class SchedulerApp(scheduleReader: ScheduleReader, scheduledMessagePublisher: ScheduledMessagePublisher)

object SchedulerApp {

  case class RunningSchedulerApp private(runningReader: ScheduleReader.Mat, runningPublisher: ScheduledMessagePublisher.Mat)

  def reader(implicit system: ActorSystem): Reader[AppConfig, SchedulerApp] =
    for {
      scheduleReader <- ScheduleReader.reader
      publisher <- ScheduledMessagePublisher.reader
    } yield SchedulerApp(scheduleReader, publisher)

  def runner(implicit system: ActorSystem, mat: ActorMaterializer): Reader[SchedulerApp, RunningSchedulerApp] = {
    Kamon.start()
    for {
      runningReader <- ScheduleReader.runner
      runningPublisher <- ScheduledMessagePublisher.runner
    } yield RunningSchedulerApp(runningReader, runningPublisher)
  }

  def stop(implicit timeout: ShutdownTimeout): Reader[RunningSchedulerApp, Unit] =
    for {
      _ <- ScheduleReader.stop
      _ <- ScheduledMessagePublisher.stop
      _ = Kamon.shutdown()
      _ = AkkaComponents.stop()
    } yield ()
}
