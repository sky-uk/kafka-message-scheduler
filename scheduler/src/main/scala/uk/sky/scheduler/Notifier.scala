package uk.sky.scheduler

import cats.Monad
import cats.effect.{Async, Deferred, Ref}
import cats.syntax.all.*

/** A synchronization primitive for notifying a waiting fiber.
  *
  * Notifier encapsulates a `Ref[F, Deferred[F, Unit]]` to provide a reusable notification mechanism. Unlike a plain
  * `Deferred`, which can only be completed once, `Notifier` can be refreshed to allow multiple notify/wait cycles.
  */
opaque type Notifier[F[_]] = Ref[F, Deferred[F, Unit]]

object Notifier {

  /** Creates a new Notifier with an initial uncompleted Deferred.
    *
    * @return
    *   a new Notifier instance
    */
  def apply[F[_] : Async]: F[Notifier[F]] = Deferred[F, Unit].flatMap(Ref.of)

  extension [F[_]](notifier: Notifier[F]) {

    /** Signals the notifier, completing the current Deferred.
      *
      * If the Deferred is already completed, this is a no-op. Multiple calls to signal before a refresh will have no
      * additional effect.
      *
      * @return
      *   F[Unit] that completes immediately
      */
    def signal(using Monad[F]): F[Unit] = notifier.get.flatMap(_.complete(())).void

    /** Waits until the notifier is signaled.
      *
      * Blocks the fiber until another fiber calls `signal` on this Notifier. If the Deferred is already completed,
      * returns immediately.
      *
      * @return
      *   F[Unit] that completes when signaled
      */
    def await(using Monad[F]): F[Unit] = notifier.get.flatMap(_.get)

    /** Refreshes the notifier with a new uncompleted Deferred.
      *
      * This allows the Notifier to be reused for another notify/wait cycle. Typically called after a wait completes and
      * before the next wait.
      *
      * @return
      *   F[Unit] that completes when the refresh is done
      */
    def refresh(using Async[F]): F[Unit] = Deferred[F, Unit].flatMap(notifier.set)
  }
}
