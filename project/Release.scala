import sbt.*
import sbtdynver.DynVerPlugin.autoImport.*

object Release {
  lazy val releaseSettings: Seq[Def.Setting[?]] = Seq(
    ThisBuild / dynverSeparator := "-"
  )
}
