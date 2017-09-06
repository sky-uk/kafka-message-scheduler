package com.sky.kms

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import cats.implicits.catsStdInstancesForFuture
import com.sky.kms.config.Configured
import com.sky.kms.streams.{ScheduleReader, ScheduledMessagePublisher}
import kamon.Kamon
import org.zalando.grafter.Rewriter._

import scala.concurrent.ExecutionContext

case class SchedulerApp(scheduleReader: ScheduleReader, scheduledMessagePublisher: ScheduledMessagePublisher)

object SchedulerApp {

  case class Running(runningReader: ScheduleReader.Mat, runningPublisher: ScheduledMessagePublisher.Mat)

  def configure(implicit system: ActorSystem): Configured[SchedulerApp] = {
    for {
      scheduleReader <- ScheduleReader.configure
      publisher <- ScheduledMessagePublisher.configure
    } yield SchedulerApp(scheduleReader, publisher)
  }.singletons

  def run(implicit system: ActorSystem, mat: ActorMaterializer): Start[Running] = {
    Kamon.start()
    for {
      runningPublisher <- ScheduledMessagePublisher.run
      runningReader <- ScheduleReader.run
    } yield Running(runningReader, runningPublisher)
  }.singletons

  def stop(implicit ec: ExecutionContext): Stop[Unit] =
    for {
      _ <- ScheduleReader.stop
      _ <- ScheduledMessagePublisher.stop
      _ <- AkkaComponents.stop
    } yield Kamon.shutdown()
}
