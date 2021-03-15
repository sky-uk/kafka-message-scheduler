package com.sky.kms

import akka.actor.{ActorRef, ActorSystem}
import akka.kafka.scaladsl.Consumer.Control
import akka.stream.ActorMaterializer
import com.sky.kms.actors._
import com.sky.kms.config.Configured
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import kamon.jmx.collector.KamonJmxMetricCollector

import scala.concurrent.Future

case class SchedulerApp(reader: ScheduleReader[Future[Control]],
                        publisher: ScheduledMessagePublisher,
                        publisherActor: ActorRef)

object SchedulerApp {

  case class Running(reader: ScheduleReader.Running[Future[Control]], publisher: ScheduledMessagePublisher.Running)

  def configure(implicit system: ActorSystem): Configured[SchedulerApp] = {
    val publisherActor  = PublisherActor.create
    val schedulingActor = SchedulingActor.create(publisherActor)
    TerminatorActor.create(schedulingActor, publisherActor)

    for {
      scheduleReader <- ScheduleReader.configure(schedulingActor)
      publisher      <- ScheduledMessagePublisher.configure
    } yield SchedulerApp(scheduleReader, publisher, publisherActor)
  }

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Running] = {
    KamonJmxMetricCollector()
    ShutdownTasks.forKamon

    for {
      publisher     <- ScheduledMessagePublisher.run
      _             <- PublisherActor.init(publisher.materializedSource)
      runningReader <- ScheduleReader.run
      running       = Running(runningReader, publisher)
      _             = ShutdownTasks.forScheduler(running)
    } yield running
  }

}
