package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.sky.kms.actors.{PublisherActor, SchedulingActor, TerminatorActor}
import com.sky.kms.config.Configured
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon

case class SchedulerApp(scheduleReader: ScheduleReader, scheduledMessagePublisher: ScheduledMessagePublisher)

object SchedulerApp extends LazyLogging {

  case class Running(runningReader: ScheduleReader.Running, runningPublisher: ScheduledMessagePublisher.Running)

  def configure(implicit system: ActorSystem): Configured[SchedulerApp] =
    for {
      scheduleReader <- ScheduleReader.configure
      publisher <- ScheduledMessagePublisher.configure
    } yield SchedulerApp(scheduleReader, publisher)

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Running] = {
    Kamon.start()
    ShutdownTasks.kamon
    for {
      runningPublisher <- ScheduledMessagePublisher.run
      publisherActor = PublisherActor create runningPublisher.materializedSource
      schedulingActor = SchedulingActor create publisherActor
      runningReader <- ScheduleReader run schedulingActor
      _ = TerminatorActor create(publisherActor, schedulingActor)
      running = Running(runningReader, runningPublisher)
      _ = ShutdownTasks.scheduler(running)
    } yield running
  }

}
