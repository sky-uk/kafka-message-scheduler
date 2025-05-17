import DockerPublish.registry
import com.tapad.docker.DockerComposePlugin.autoImport.*
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.*
import sbt.*
import sbt.Keys.*

object DockerCompose {
  def settings(project: Project): Seq[Def.Setting[?]] = Seq(
    variablesForSubstitution ++= Map(
      "CONTAINER_REPOSITORY" -> registry
    ),
    dockerImageCreationTask := (project / Docker / publishLocal).value,
    composeFile             := "it/docker/docker-compose.yml"
  )
}
