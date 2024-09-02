lazy val scala3 = "3.5.0"
lazy val scmUrl = "https://github.com/sky-uk/kafka-message-scheduler"

ThisBuild / scalaVersion := scala3
ThisBuild / organization := "uk.sky"
ThisBuild / licenses     := List("BSD New" -> url("https://opensource.org/licenses/BSD-3-Clause"))
ThisBuild / homepage     := Some(url(scmUrl))

ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

ThisBuild / dynverSeparator := "-"

ThisBuild / versionScheme := Some("early-semver")

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / scalafmtOnCompile    := true

lazy val scheduler = project
  .settings(CommonSettings.default)
  .settings(CommonSettings.buildInfo)
  .settings {
    Seq(
      dockerRepository     := sys.env.get("DOCKER_REPOSITORY"),
      dockerBaseImage      := "eclipse-temurin:21-jre-jammy",
      Docker / packageName := "kafka-message-scheduler",
      dockerUpdateLatest   := true
    )
  }

val schema    = inputKey[Unit]("Generate the Avro schema file for the Schedule schema.")
lazy val avro = project
  .dependsOn(scheduler)
  .settings(schema := (Compile / run).toTask("").value)

lazy val it = Project("integration-test", file("it"))
  .settings(CommonSettings.default)
  .settings {
    Seq(
      dockerImageCreationTask := (scheduler / Docker / publishLocal).value,
      composeFile             := "it/docker/docker-compose.yml"
    )
  }
  .dependsOn(scheduler % "compile->compile;test->test")

lazy val root = Project("kafka-message-scheduler", file("."))
  .aggregate(scheduler, avro, it)

run / fork  := true
Test / fork := true
