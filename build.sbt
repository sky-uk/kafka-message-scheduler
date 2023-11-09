import com.typesafe.sbt.packager.docker.Cmd

lazy val scala3 = "3.3.1"
lazy val scmUrl = "https://github.com/sky-uk/kafka-message-scheduler"

ThisBuild / scalaVersion := scala3
ThisBuild / organization := "uk.sky"
ThisBuild / licenses     := List("BSD New" -> url("https://opensource.org/licenses/BSD-3-Clause"))
ThisBuild / homepage     := Some(url(scmUrl))

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / scalafmtOnCompile    := true

lazy val scheduler = project
  .settings(CommonSettings.default)
  .settings {
    Seq(
      dockerBaseImage      := "alpine:3.17.2",
      Docker / packageName := "kafka-message-scheduler",
      dockerUpdateLatest   := true,
      dockerCommands ++= Seq(
        Cmd("USER", "root"),
        Cmd("RUN", "apk add --no-cache bash openjdk17")
      )
    )
  }

lazy val avro = project.dependsOn(scheduler)

lazy val it = Project("integration-test", file("it"))
  .settings(CommonSettings.default)
  .settings {
    Seq(
      dockerImageCreationTask := (scheduler / Docker / publishLocal).value,
      composeFile             := "it/docker/docker-compose.yml"
    )
  }
  .dependsOn(scheduler)

lazy val root = Project("kafka-message-scheduler", file("."))
  .aggregate(scheduler, avro, it)

run / fork  := true
Test / fork := true
