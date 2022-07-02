import sbt._

object Aliases {

  private lazy val avroBuild      = "project avro; test; schema"
  private lazy val schedulerBuild = "project scheduler; test; dockerComposeTest"
  private lazy val linters        =
    addCommandAlias("checkFix", "scalafixAll --check OrganizeImports; scalafixAll --check") ++
      addCommandAlias("runFix", "scalafixAll OrganizeImports; scalafixAll") ++
      addCommandAlias("checkFmt", "scalafmtCheckAll; scalafmtSbtCheck") ++
      addCommandAlias("runFmt", "scalafmtAll; scalafmtSbt")

  lazy val defineCommandAliases =
    addCommandAlias("ciBuild", s"checkFmt; checkFix; $avroBuild; $schedulerBuild;") ++
      addCommandAlias("ciRelease", "clean; schema; project scheduler; release with-defaults") ++
      linters

}
