import sbt._

object Aliases {

  lazy val defineCommandAliases =
    addCommandAlias("ciBuild", "checkFmt; checkFix; test; schema") ++
      addCommandAlias("ciRelease", "clean; schema; project scheduler; release with-defaults") ++
      addCommandAlias("checkFix", "scalafixAll --check OrganizeImports; scalafixAll --check") ++
      addCommandAlias("runFix", "scalafixAll OrganizeImports; scalafixAll") ++
      addCommandAlias("checkFmt", "scalafmtCheckAll; scalafmtSbtCheck") ++
      addCommandAlias("runFmt", "scalafmtAll; scalafmtSbt")

}
