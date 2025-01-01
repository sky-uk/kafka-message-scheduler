package uk.sky.scheduler.avro

import cats.syntax.all.*
import com.monovore.decline.Opts
import fs2.io.file.Path

final case class Config(file: Option[String], path: Option[Path], prettyPrint: Boolean)

object Config {
  private val fileOpts: Opts[Option[String]] = Opts
    .option[String](
      long = "file",
      help = "Optional file name for generated schema file.",
      short = "f",
      metavar = "file"
    )
    .orNone

  private val pathOpts: Opts[Option[Path]] = Opts
    .option[String](
      long = "path",
      help = "Optional file path to the generated schema file.",
      short = "p",
      metavar = "path"
    )
    .map(Path.apply)
    .orNone

  private val prettyPrintOpts: Opts[Boolean] = Opts
    .flag(
      long = "pretty-print",
      help = "pretty-print the generated schema."
    )
    .orFalse

  val opts: Opts[Config] = (fileOpts, pathOpts, prettyPrintOpts).mapN(Config.apply)
}
