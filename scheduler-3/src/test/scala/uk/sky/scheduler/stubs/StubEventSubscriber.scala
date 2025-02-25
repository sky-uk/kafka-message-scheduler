package uk.sky.scheduler.stubs

import cats.effect.Async
import cats.effect.std.Queue
import cats.syntax.all.*
import fs2.*
import uk.sky.scheduler.EventSubscriber
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.message.Message

final case class StubEventSubscriber[F[_] : Async](
                                                    input: Queue[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]]
                                                  ) extends EventSubscriber[F] {
  override def messages: Stream[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]] =
    Stream.fromQueueUnterminated(input)
}

object StubEventSubscriber {
  def apply[F[_] : Async]: F[StubEventSubscriber[F]] =
    for {
      input <- Queue.unbounded[F, Message[Either[ScheduleError, Option[ScheduleEvent]]]]
    } yield StubEventSubscriber(input)
}