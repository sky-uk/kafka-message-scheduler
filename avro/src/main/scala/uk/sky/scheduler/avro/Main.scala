package uk.sky.scheduler.avro

import cats.effect.Resource.ExitCase
import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import fs2.*
import fs2.io.file.*
import uk.sky.scheduler.kafka.avro.avroScheduleCodec

object Main extends IOApp.Simple {

  val schemaPath: Path = Path("target/schemas/schedule.avsc")

  override def run: IO[Unit] = {
    for {
      schema <- Stream.fromEither[IO](avroScheduleCodec.schema.leftMap(_.throwable))
      _      <- Stream.eval(Files[IO].createDirectories(Path("target/schemas")))
      _      <- Stream(schema.toString(true))
                  .through(text.utf8.encode)
                  .through(Files[IO].writeAll(schemaPath))
    } yield ()
  }.onFinalizeCase {
    case ExitCase.Succeeded  => IO.println(s"Generated Schema file: $schemaPath")
    case ExitCase.Errored(e) => IO.println(s"Error creating Schema file: $e")
    case ExitCase.Canceled   => IO.println("Canceled")
  }.compile.drain

}
