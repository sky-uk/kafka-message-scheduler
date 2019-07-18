import sbt._

object Aliases {

  lazy val defineCommandAliases = {
    addCommandAlias("ciBuild", ";clean; test; schema") ++
      addCommandAlias("ciRelease", ";clean; schema; project scheduler; release with-defaults") ++
      addCommandAlias("checkFmt", ";scalafmt::test; test:scalafmt::test; sbt:scalafmt::test") ++
      addCommandAlias("runFmt", ";scalafmt; test:scalafmt; sbt:scalafmt")
  }
}
