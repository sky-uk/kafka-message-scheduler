import com.typesafe.sbt.packager.Keys.*
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{dockerBuildxPlatforms, Docker}
import sbt.*

object DockerPublish {

  lazy val dockerSettings: Seq[Def.Setting[?]] = imageSettings

  private lazy val imageSettings = Seq(
    Docker / packageName := "kafka-message-scheduler",
    dockerBaseImage      := "alpine:3.21",
    dockerRepository     := Some(registry),
    dockerLabels         := Map("maintainer" -> "Sky"),
    dockerUpdateLatest   := true,
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "apk add --no-cache bash openjdk17")
    ),
    dockerAliases ++= additionalRegistries.map(host => dockerAlias.value.withRegistryHost(Some(host)))
  )

  lazy val allRegistries: List[String]      = sys.env.get("CONTAINER_REPOSITORIES").fold(List("test"))(_.split(" ").toList)
  lazy val (registry, additionalRegistries) = allRegistries match {
    case registry :: additionalRegistries => registry -> additionalRegistries
    case other                            => sys.error("Expected at least one Docker registry")
  }
}
