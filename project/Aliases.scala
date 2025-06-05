import sbt.*

object Aliases {

  type Settings = Seq[Def.Setting[?]]

  val ModuleName = "kafka-message-scheduler"

  private def alias(name: String, tasks: List[String]): Settings =
    addCommandAlias(s"$ModuleName-$name", tasks.mkString("; "))

  private def cdBuild(module: String): List[String] =
    List(
      s"project $module",
      "Docker / publish"
    )

  private def scalaPrBuild(module: String): List[String] =
    List(
      "checkFix",
      "checkFmt",
      s"project $module",
      "test",
      "project /",
      "schema",
      "project it",
      "dockerComposeUp",
      "test",
      "dockerComposeStop"
    )

  lazy val linting: Settings =
    addCommandAlias("checkFix", "scalafixAll --check OrganizeImports; scalafixAll --check") ++
      addCommandAlias("runFix", "scalafixAll OrganizeImports; scalafixAll") ++
      addCommandAlias("checkFmt", "scalafmtCheckAll; scalafmtSbtCheck") ++
      addCommandAlias("runFmt", "scalafmtAll; scalafmtSbt") ++
      addCommandAlias("checkLint", "checkFmt; checkFix") ++
      addCommandAlias("runLint", "runFmt; runFix")

  lazy val core: Settings =
    alias("cdBuild", scalaPrBuild("scheduler") ++ cdBuild("scheduler")) ++
      alias("prBuild", scalaPrBuild("scheduler")) ++
      linting

}
