package uk.sky.scheduler

import cats.effect.*
import fs2.Stream

object Main extends IOApp.Simple {

  def stream[F[_] : Concurrent]: Stream[F, Unit] =
    for {
      scheduler <- Stream.resource(Scheduler.live[F])
      message   <- scheduler.stream
    } yield message

  override def run: IO[Unit] = stream[IO].compile.drain

}
