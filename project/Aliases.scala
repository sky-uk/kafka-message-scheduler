import sbt.*

object Aliases {

  type Settings = Seq[Def.Setting[?]]

  val ModuleName = "kafka-message-scheduler"

  def alias(name: String, value: String): Settings = addCommandAlias(s"$ModuleName-$name", value)

  def cdBuild(module: String) =
    s"checkFix; checkFmt; project $module; test; release with-defaults;"

  def scalaPrBuild(module: String) =
    s"checkFix; checkFmt; project $module; test; project it; dockerComposeTest"

  lazy val linting: Settings =
    addCommandAlias("checkFix", "scalafixAll --check OrganizeImports; scalafixAll --check") ++
      addCommandAlias("runFix", "scalafixAll OrganizeImports; scalafixAll") ++
      addCommandAlias("checkFmt", "scalafmtCheckAll; scalafmtSbtCheck") ++
      addCommandAlias("runFmt", "scalafmtAll; scalafmtSbt") ++
      addCommandAlias("checkLint", "checkFmt; checkFix") ++
      addCommandAlias("runLint", "runFmt; runFix")

  lazy val core: Settings =
    alias("cdBuild", cdBuild("scheduler")) ++
      alias("prBuild", scalaPrBuild("scheduler")) ++
      linting

}
