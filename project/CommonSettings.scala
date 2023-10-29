import org.typelevel.sbt.tpolecat.TpolecatPlugin.autoImport.{tpolecatExcludeOptions, tpolecatScalacOptions}
import org.typelevel.scalacoptions.ScalacOptions
import sbt.*

object CommonSettings {

  val default: Seq[Def.Setting[?]] = Seq(
    tpolecatScalacOptions ++= Set(
      ScalacOptions.other("-no-indent"),
      ScalacOptions.other("-old-syntax"),
      ScalacOptions.other("-Wunused:all"),
      ScalacOptions.other("-Wnonunit-statement")
    ),
    Test / tpolecatExcludeOptions += ScalacOptions.warnNonUnitStatement
  )

}
