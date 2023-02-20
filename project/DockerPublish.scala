import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.docker.Cmd
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker
import sbt.Keys._
import sbt._

import scala.sys.process.Process

object DockerPublish {

  lazy val dockerSettings = imageSettings ++ dockerBuildxSettings

  lazy val ensureDockerBuildx    = taskKey[Unit]("Ensure that docker buildx configuration exists")
  lazy val dockerBuildWithBuildx = taskKey[Unit]("Build docker images using buildx")

  private lazy val imageSettings = Seq(
    Docker / packageName := "kafka-message-scheduler",
    dockerBaseImage      := "alpine:3.17.2",
    dockerRepository     := Some("skyuk"),
    dockerLabels         := Map("maintainer" -> "Sky"),
    dockerUpdateLatest   := true,
    dockerCommands ++= Seq(
      Cmd("USER", "root"),
      Cmd("RUN", "apk add --no-cache bash openjdk17")
    )
  )

  private lazy val dockerBuildxSettings = Seq(
    ensureDockerBuildx    := {
      if (Process("docker buildx inspect multi-arch-builder").! == 1) {
        Process("docker buildx create --use --name multi-arch-builder", baseDirectory.value).!
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
