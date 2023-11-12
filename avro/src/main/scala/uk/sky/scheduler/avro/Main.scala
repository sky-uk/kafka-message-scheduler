package uk.sky.scheduler.avro

import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import fs2.*
import fs2.io.file.*
import uk.sky.scheduler.kafka.avro.avroScheduleCodec

object Main extends IOApp.Simple {

  override def run: IO[Unit] = {
    val path = Path("target/schemas/schedule.avsc")
    {
      for {
        schema <- Stream.fromEither[IO](avroScheduleCodec.schema.leftMap(_.throwable))
        _      <- Stream.eval(Files[IO].createDirectories(Path("target/schemas")))
        _      <- Stream(schema.toString(true))
                    .through(text.utf8.encode)
                    .through(Files[IO].writeAll(path))
      } yield ()
    }.compile.drain *> IO.println(s"Generated schema file: $path")
  }

}
