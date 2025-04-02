import com.typesafe.sbt.packager.Keys.*
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import sbt.Keys.*
import sbt.*

import scala.sys.process.Process

object DockerPublish {

  lazy val dockerSettings = imageSettings // ++ dockerBuildxSettings

  lazy val ensureDockerBuildx    = taskKey[Unit]("Ensure that docker buildx configuration exists")
  lazy val dockerBuildWithBuildx = taskKey[Unit]("Build docker images using buildx")

  private lazy val imageSettings = Seq(
    Docker / packageName := "kafka-message-scheduler",
    dockerBaseImage      := "alpine:3.21",
    dockerRepository     := registry,
    dockerLabels         := Map("maintainer" -> "Sky"),
    dockerUpdateLatest   := true,
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "apk add --no-cache bash openjdk17")
    ),
    dockerAliases ++= additionalRegistries.map(host => dockerAlias.value.withRegistryHost(Some(host)))
  )

  val allRegistries                     = sys.env.get("CONTAINER_REPOSITORIES").fold(List("test"))(_.split(" ").toList)
  val registry                          = allRegistries.headOption // Provide a docker registry host
  val additionalRegistries              = allRegistries.drop(1)    // Remove the first host, because it is already provide.
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
