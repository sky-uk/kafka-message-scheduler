package uk.sky.scheduler

import cats.Parallel
import cats.data.ReaderT
import cats.effect.*
import cats.effect.std.Supervisor
import cats.syntax.all.*
import fs2.Stream
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.config.Config
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.message.Message
import uk.sky.scheduler.message.Metadata.*
import uk.sky.scheduler.syntax.all.*

class Scheduler[F[_] : Concurrent, O](
    eventSubscriber: EventSubscriber[F],
    scheduleQueue: ScheduleQueue[F],
    schedulePublisher: SchedulePublisher[F, O]
) {
  private val scheduleEvents =
    eventSubscriber.messages
      .mapChunks(_.dedupeBy(_.key))
      .evalTapChunk { case Message(key, _, value, metadata) =>
        value match {
          case Left(_)               => scheduleQueue.cancel(key)
          case Right(None)           => scheduleQueue.cancel(key).unlessA(metadata.isExpired)
          case Right(Some(schedule)) => scheduleQueue.schedule(key, schedule)
        }
      }

  def stream: Stream[F, O] =
    scheduleEvents.drain
      .merge(scheduleQueue.schedules.through(schedulePublisher.publish))
}

object Scheduler {
  def live[F[_] : Async : Parallel : LoggerFactory : Meter](
      supervisor: Supervisor[F]
  ): ReaderT[F, Config, Scheduler[F, Unit]] =
    for {
      schedulePublisher <- SchedulePublisher.live[F].lift
      scheduler         <- ReaderT[F, Config, Scheduler[F, Unit]] { conf =>
                             for {
                               allowEnqueue  <- Deferred[F, Unit]
                               scheduleQueue <- ScheduleQueue.live(allowEnqueue, supervisor)
                               subscriber    <- EventSubscriber.live[F](conf, allowEnqueue)
                             } yield new Scheduler(subscriber, scheduleQueue, schedulePublisher)
                           }

    } yield scheduler
}
