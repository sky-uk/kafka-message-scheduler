package uk.sky.scheduler.avro

import buildinfo.BuildInfo
import cats.effect.Resource.ExitCase
import cats.effect.std.Console
import cats.effect.{ExitCode, IO}
import cats.syntax.all.*
import com.monovore.decline.Opts
import com.monovore.decline.effect.*
import fs2.*
import fs2.io.file.*
import uk.sky.scheduler.kafka.avro.avroScheduleCodec

object Main
    extends CommandIOApp(
      name = "schema",
      header = "Generate the Avro schema file for the Schedule schema",
      version = BuildInfo.version
    ) {

  override def main: Opts[IO[ExitCode]] = Config.opts.map { case Config(file, path, prettyPrint) =>
    val basePath = path.getOrElse(Path("target/schemas"))
    val fileName = file.getOrElse("schedule.avsc")

    val schemaPath: Path = basePath / fileName

    val stream = for {
      schema <- Stream.fromEither[IO](avroScheduleCodec.schema.leftMap(_.throwable))
      _      <- Stream.eval(Files[IO].createDirectories(basePath))
      _      <- Stream(schema.toString(prettyPrint))
                  .through(text.utf8.encode)
                  .through(Files[IO].writeAll(schemaPath))
    } yield ()

    stream.onFinalizeCase {
      case ExitCase.Succeeded =>
        IO.println(s"Generated Schema file: ${schemaPath.absolute}")

      case ExitCase.Errored(e) =>
        Console[IO].errorln(s"Error creating Schema file: $e") *> Console[IO].printStackTrace(e)

      case ExitCase.Canceled =>
        Console[IO].errorln("Canceled")
    }.compile.drain.as(ExitCode.Success)
  }

}
