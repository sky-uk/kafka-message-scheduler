package uk.sky.scheduler

import cats.effect.*
import fs2.Stream

object Main extends IOApp.Simple {

  def stream[IO[_] : Concurrent]: Stream[IO, Unit] =
    for {
      scheduler <- Stream.resource(Scheduler.live[IO])
      message   <- scheduler.stream
    } yield message

  override def run: IO[Unit] = stream[IO].compile.drain

}
