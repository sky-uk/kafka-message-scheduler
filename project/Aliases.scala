import sbt._

object Aliases {

  lazy val defineCommandAliases = {
    addCommandAlias("ciBuild", ";clean; test") ++
      addCommandAlias("ciRelease", ";clean; release with-defaults")
  }
}
