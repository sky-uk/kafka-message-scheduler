import Release.*
import DockerPublish.*
import org.typelevel.scalacoptions.ScalacOptions
import DockerComposeSettings.*

ThisBuild / organization := "com.sky"

ThisBuild / scalafmtOnCompile := true
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

Global / onChangedBuildSource := ReloadOnSourceChanges

val scala3Settings = Seq(
  scalaVersion             := "3.6.2",
  tpolecatScalacOptions ++= Set(
    ScalacOptions.other("-no-indent"),
    ScalacOptions.other("-old-syntax")
  ),
  Test / tpolecatExcludeOptions += ScalacOptions.warnNonUnitStatement,
  run / fork               := true,
  Test / fork              := true,
  Test / parallelExecution := false
)

val buildInfoSettings = (pkg: String) =>
  Seq(
    buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := pkg
  )

lazy val scheduler = (project in file("scheduler"))
  .enablePlugins(JavaAgent, DockerPlugin, JavaAppPackaging, BuildInfoPlugin)
  .settings(scala3Settings)
  .settings(javaOptions += "-Dotel.java.global-autoconfigure.enabled=true")
  .settings(javaAgents += Dependencies.OpenTelemetry.javaAgent)
  .settings(
    libraryDependencies ++= Dependencies.core,
    buildInfoSettings("uk.sky"),
    dockerSettings,
    releaseSettings
  )

lazy val it = (project in file("it"))
  .enablePlugins(DockerComposePlugin)
  .settings(scala3Settings)
  .settings {
    Seq(
      libraryDependencies ++= Dependencies.it,
      Test / fork             := true,
      dockerImageCreationTask := (scheduler / Docker / publishLocal).value,
      composeFile             := "it/docker/docker-compose.yml"
    )
  }
  .settings(settings)
  .settings(Seq(envVars := Map(kafkaPort)))
  .dependsOn(scheduler % "compile->compile;test->test")

val schema = inputKey[Unit]("Generate the Avro schema file for the Schedule schema.")

lazy val avro = (project in file("avro"))
  .settings(scala3Settings)
  .settings(libraryDependencies += Dependencies.avro4s)
  .settings(schema := (Compile / run).toTask("").value)
  .dependsOn(scheduler)
  .disablePlugins(ReleasePlugin)

lazy val root = (project in file("."))
  .withId("kafka-message-scheduler")
  .aggregate(scheduler, it)
  .enablePlugins(DockerComposePlugin)
  .disablePlugins(ReleasePlugin)
  .settings(Aliases.core)
