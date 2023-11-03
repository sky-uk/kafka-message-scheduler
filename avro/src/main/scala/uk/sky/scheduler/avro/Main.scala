package uk.sky.scheduler.avro

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import cats.data.EitherT
import cats.effect.{IO, IOApp}
import cats.syntax.all.*
import uk.sky.scheduler.kafka.avro.avroScheduleCodec

object Main extends IOApp.Simple {

  override def run: IO[Unit] = {
    for {
      outputDir <- EitherT.liftF(IO.blocking(Paths.get("avro/target/schemas")))
      _         <- EitherT.liftF(IO.blocking(Files.createDirectories(outputDir)))
      schema    <- EitherT.fromEither(avroScheduleCodec.schema)
      path      <- EitherT.liftF(IO.blocking(outputDir.resolve(Paths.get("schedule.avsc"))))
      _         <- EitherT.liftF(IO.println(s"Generated schema file: $path"))
      _         <- EitherT.liftF(IO.blocking(Files.write(path, schema.toString(true).getBytes(StandardCharsets.UTF_8))))
    } yield ()
  }.foldF(avroError => IO.raiseError(avroError.throwable), _.pure)

}
