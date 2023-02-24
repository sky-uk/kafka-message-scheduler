lazy val avroBuild      = "project avro; test; schema"
lazy val schedulerBuild = "project scheduler; test; dockerComposeTest"

addCommandAlias("checkFix", "scalafixAll --check OrganizeImports; scalafixAll --check")
addCommandAlias("runFix", "scalafixAll OrganizeImports; scalafixAll")
addCommandAlias("checkFmt", "scalafmtCheckAll; scalafmtSbtCheck")
addCommandAlias("runFmt", "scalafmtAll; scalafmtSbt")

addCommandAlias("ciBuild", s"checkFmt; checkFix; $avroBuild; $schedulerBuild;")
addCommandAlias("ciRelease", "clean; schema; project scheduler; release with-defaults")
