import sbt.{Def, Task, addCommandAlias}

object Aliases {

  type Settings = Seq[Def.Setting[_]]

  val ModuleName = "kafka-message-scheduler"

  def alias(name: String, value: String): Settings = addCommandAlias(s"$ModuleName-$name", value)

  def cdBuild(module: String) = s"project $module; test; release;"

  def scalaPrBuild(module: String) =
    s"project $module; test;"

  lazy val core: Settings =
    alias("cdBuild", cdBuild("scheduler3")) ++
      alias("prBuild", scalaPrBuild("scheduler3"))
}
