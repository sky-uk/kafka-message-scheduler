package com.sky.kms

import akka.actor.{ActorRef, ActorSystem}
import akka.stream.ActorMaterializer
import com.sky.kms.actors.{PublisherActor, SchedulingActor, TerminatorActor}
import com.sky.kms.config.Configured
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import com.typesafe.scalalogging.LazyLogging
import kamon.Kamon

case class SchedulerApp(scheduleReader: ScheduleReader, scheduledMessagePublisher: ScheduledMessagePublisher, publisherActor: ActorRef)

object SchedulerApp extends LazyLogging {

  case class Running(runningReader: ScheduleReader.Running, runningPublisher: ScheduledMessagePublisher.Running)

  def configure(implicit system: ActorSystem): Configured[SchedulerApp] =
    for {
      publisherActor <- PublisherActor.configure
      schedulingActor = SchedulingActor.create(publisherActor)
      scheduleReader <- ScheduleReader.configure(schedulingActor)
      publisher <- ScheduledMessagePublisher.configure
      _ = TerminatorActor.create(scheduleReader.schedulingActor, publisherActor)
    } yield SchedulerApp(scheduleReader, publisher, publisherActor)

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Running] = {
    Kamon.start()
    ShutdownTasks.forKamon
    for {
      runningPublisher <- ScheduledMessagePublisher.run
      queue = runningPublisher.materializedSource
      _ <- PublisherActor.init(queue)
      runningReader <- ScheduleReader.run
      running = Running(runningReader, runningPublisher)
      _ = ShutdownTasks.forScheduler(running)
    } yield running
  }

}
