import sbt.{addCommandAlias, Def, Task}

object Aliases {

  type Settings = Seq[Def.Setting[?]]

  val ModuleName = "kafka-message-scheduler"

  def alias(name: String, value: String): Settings = addCommandAlias(s"$ModuleName-$name", value)

  def cdBuild(module: String) =
    s"project it; dockerComposeUp; test;"
//    s"checkFmt; project $module; checkFix; test; project it; checkFix; dockerComposeUp; test; release with-defaults;"

  def scalaPrBuild(module: String) =
    s"checkFmt; project $module; checkFix; test; project it; checkFix; dockerComposeUp; test"

  lazy val core: Settings =
    alias("cdBuild", cdBuild("scheduler3")) ++
      alias("prBuild", scalaPrBuild("scheduler3"))
}
