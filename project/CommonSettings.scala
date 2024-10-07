import org.typelevel.sbt.tpolecat.TpolecatPlugin.autoImport.{tpolecatExcludeOptions, tpolecatScalacOptions}
import org.typelevel.scalacoptions.ScalacOptions
import sbt.*
import sbt.Keys.*
import sbtbuildinfo.*
import sbtbuildinfo.BuildInfoKeys.*

object CommonSettings {

  val buildInfo: Def.Setting[?] = buildInfoKeys := Seq[BuildInfoKey](name, version)

  val default: Seq[Def.Setting[?]] = Seq(
    tpolecatScalacOptions ++= Set(
      ScalacOptions.other("-no-indent"),
      ScalacOptions.other("-old-syntax")
    ),
    Test / tpolecatExcludeOptions += ScalacOptions.warnNonUnitStatement
  )

}
