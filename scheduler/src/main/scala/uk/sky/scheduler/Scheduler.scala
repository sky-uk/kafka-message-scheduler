package uk.sky.scheduler

import cats.Parallel
import cats.effect.Resource.ExitCase
import cats.effect.std.Queue
import cats.effect.{Async, Concurrent, Deferred}
import cats.syntax.all.*
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.config.Config
import uk.sky.scheduler.domain.ScheduleEvent

class Scheduler[F[_] : Concurrent : LoggerFactory, O](
    eventSubscriber: EventSubscriber[F],
    scheduleQueue: ScheduleQueue[F],
    schedulePublisher: SchedulePublisher[F, O]
) {
  private val logger = LoggerFactory[F].getLogger

  private val scheduleStream = eventSubscriber.messages.evalMapChunk { message =>
    message.value match {
      case Left(_) | Right(None) => if (message.expired) Concurrent[F].unit else scheduleQueue.cancel(message.key)
      case Right(Some(schedule)) => scheduleQueue.schedule(message.key, schedule).void
    }
  }

  def stream: Stream[F, O] =
    scheduleStream.drain.merge(schedulePublisher.publish).onFinalizeCase {
      case ExitCase.Succeeded  => logger.info("Stream Succeeded")
      case ExitCase.Errored(e) => logger.error(e)(s"Stream error - ${e.getMessage}")
      case ExitCase.Canceled   => logger.info("Stream canceled")
    }
}

object Scheduler {
  def live[F[_] : Async : Parallel : LoggerFactory : Meter](config: Config): F[Scheduler[F, Unit]] =
    for {
      eventQueue       <- Queue.unbounded[F, ScheduleEvent]
      allowEnqueue     <- Deferred[F, Unit]
      scheduleQueue    <- ScheduleQueue.live[F](eventQueue, allowEnqueue)
      eventSubscriber  <- EventSubscriber.live[F](config.scheduler, allowEnqueue)
      schedulePublisher = SchedulePublisher.kafka[F](config.scheduler, eventQueue)
    } yield new Scheduler[F, Unit](eventSubscriber, scheduleQueue, schedulePublisher)
}
