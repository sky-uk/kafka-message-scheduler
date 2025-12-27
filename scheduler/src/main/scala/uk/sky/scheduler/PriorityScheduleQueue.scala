package uk.sky.scheduler

import cats.effect.{Ref, Sync}
import cats.syntax.all.*
import uk.sky.scheduler.domain.ScheduleEvent

import scala.collection.immutable.TreeMap

/** Priority queue for schedules, ordered by time (earliest first).
  *
  * Uses TreeMap with composite key (time, key) to maintain ordering while ensuring uniqueness.
  */
trait PriorityScheduleQueue[F[_]] {
  def peek: F[Option[(String, ScheduleEvent)]]
  def enqueue(key: String, scheduleEvent: ScheduleEvent): F[Unit]
  def dequeue: F[Option[(String, ScheduleEvent)]]
  def remove(key: String): F[Unit]
}

object PriorityScheduleQueue {

  private case class State(
      queue: TreeMap[(Long, String), ScheduleEvent],
      keyIndex: Map[String, Long]
  ) {
    def removeKey(key: String): State =
      keyIndex.get(key) match {
        case Some(time) =>
          State(
            queue = queue - (time -> key),
            keyIndex = keyIndex - key
          )

        case None => this
      }
  }

  private object State {
    val empty: State = State(TreeMap.empty[(Long, String), ScheduleEvent], Map.empty)
  }

  def apply[F[_] : Sync]: F[PriorityScheduleQueue[F]] =
    for {
      state <- Ref.of[F, State](State.empty)
    } yield new PriorityScheduleQueue[F] {

      override def peek: F[Option[(String, ScheduleEvent)]] =
        state.get
          .map(_.queue.headOption.map { case ((_, key), event) => key -> event })

      override def enqueue(key: String, scheduleEvent: ScheduleEvent): F[Unit] =
        state.update { state =>
          val stateWithoutOld = state.removeKey(key)
          val time            = scheduleEvent.schedule.time
          State(
            queue = stateWithoutOld.queue.updated((time, key), scheduleEvent),
            keyIndex = stateWithoutOld.keyIndex.updated(key, time)
          )
        }

      override def dequeue: F[Option[(String, ScheduleEvent)]] =
        state.modify { state =>
          state.queue.headOption match {
            case Some(((time, key), event)) =>
              val newState = State(
                queue = state.queue.tail,
                keyIndex = state.keyIndex - key
              )
              (newState, Some((key, event)))

            case None => (state, None)
          }
        }

      override def remove(key: String): F[Unit] =
        state.update(_.removeKey(key))
    }

}
