import com.typesafe.sbt.packager.Keys.*
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import sbt.*
import sbt.Keys.*

import scala.sys.process.Process

object DockerPublish {

  lazy val dockerSettings: Seq[Def.Setting[?]] = imageSettings ++ dockerBuildxSettings

  lazy val ensureDockerBuildx    = taskKey[Unit]("Ensure that docker buildx configuration exists")
  lazy val dockerBuildWithBuildx = taskKey[Unit]("Build docker images using buildx")

  private lazy val imageSettings = Seq(
    Docker / packageName := "kafka-message-scheduler",
    dockerBaseImage      := "eclipse-temurin:21-jre-jammy",
    dockerRepository     := Some(registry),
    dockerLabels         := Map("maintainer" -> "Sky"),
    dockerUpdateLatest   := true,
    dockerAliases ++= additionalRegistries.map(host => dockerAlias.value.withRegistryHost(Some(host)))
  )

  lazy val allRegistries: List[String]      = sys.env.get("CONTAINER_REPOSITORIES").fold(List("test"))(_.split(" ").toList)
  lazy val (registry, additionalRegistries) = allRegistries match {
    case registry :: additionalRegistries => registry -> additionalRegistries
    case other                            => sys.error("Expected at least one Docker registry")
  }

  private lazy val dockerBuildxSettings = Seq(
    ensureDockerBuildx    := {
      if (Process("docker buildx inspect multi-arch-builder").! == 1) {
        Process("docker context create multi-arch-context", baseDirectory.value).!
        Process(
          "docker buildx create --name multiple-arch-builder --use multi-arch-context",
          baseDirectory.value
        ).!
      }
    },
    dockerBuildWithBuildx := {
      streams.value.log("Building and pushing image with Buildx")
      dockerAliases.value.foreach { alias =>
        Process(
          "docker buildx build --platform=linux/arm64,linux/amd64 --push -t " +
            alias + " .",
          baseDirectory.value / "target" / "docker" / "stage"
        ).!
      }
    },
    Docker / publish      := Def
      .sequential(
        Docker / publishLocal,
        ensureDockerBuildx,
        dockerBuildWithBuildx
      )
      .value
  )
}
