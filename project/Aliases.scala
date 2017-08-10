import sbt._

object Aliases {

  lazy val defineCommandAliases = {
    addCommandAlias("ciBuild", ";clean; test; schema") ++
      addCommandAlias("ciRelease", ";clean; schema; project scheduler; release with-defaults")
  }
}
