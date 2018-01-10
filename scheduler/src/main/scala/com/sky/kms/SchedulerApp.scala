package com.sky.kms

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.stream.ActorMaterializer
import com.sky.kms.config.Configured
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import kamon.Kamon

import scala.concurrent.Future

case class SchedulerApp(scheduleReader: ScheduleReader, scheduledMessagePublisher: ScheduledMessagePublisher)

object SchedulerApp {

  case class Running(runningReader: ScheduleReader.Running, runningPublisher: ScheduledMessagePublisher.Running)

  def configure(implicit system: ActorSystem): Configured[SchedulerApp] =
    for {
      scheduleReader <- ScheduleReader.configure
      publisher <- ScheduledMessagePublisher.configure
    } yield SchedulerApp(scheduleReader, publisher)

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Running] = {
    Kamon.start()
    for {
      runningPublisher <- ScheduledMessagePublisher.run
      runningReader <- ScheduleReader.run(runningPublisher.materializedSource)
      running = Running(runningReader, runningPublisher)
      _ = shutdownAppOnFailure(running)
      _ = configureKamonShutdown
    } yield running
  }

  private def shutdownAppOnFailure(runningScheduler: Running)(implicit system: ActorSystem): Unit =
    runningScheduler.runningPublisher.materializedSink.failed.foreach(_ => CoordinatedShutdown(system).run())(system.dispatcher)

  private def configureKamonShutdown(implicit system: ActorSystem): Unit =
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseBeforeActorSystemTerminate, "shutdown-kamon") { () =>
      Kamon.shutdown()
      Future.successful(Done)
    }

}
