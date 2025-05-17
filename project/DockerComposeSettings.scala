import DockerPublish.registry
import com.tapad.docker.DockerComposePlugin.autoImport.variablesForSubstitution
import sbt.Def

object DockerComposeSettings {
  lazy val settings: Seq[Def.Setting[?]] = Seq(
    variablesForSubstitution ++= Map(
      "CONTAINER_REPOSITORY" -> registry
    )
  )
}
