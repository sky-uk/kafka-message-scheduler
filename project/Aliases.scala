import sbt.{addCommandAlias, Def}

object Aliases {

  type Settings = Seq[Def.Setting[?]]

  val ModuleName = "kafka-message-scheduler"

  def alias(name: String, value: String): Settings = addCommandAlias(s"$ModuleName-$name", value)

  def cdBuild(module: String) =
    s"project $module;  release with-defaults;"
//    s"checkFix; checkFmt; project $module; test; release with-defaults;"

  def scalaPrBuild(module: String) =
    s"project $module"
//    s"checkFix; checkFmt; project $module; test; project it; dockerComposeUp; test"

  lazy val core: Settings =
    alias("cdBuild", cdBuild("scheduler")) ++
      alias("prBuild", scalaPrBuild("scheduler"))
}
