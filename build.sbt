import DockerPublish.*
import Release.*
import org.typelevel.scalacoptions.ScalacOptions

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
  .settings(libraryDependencies ++= Dependencies.it)
  .settings(DockerCompose.settings(scheduler))
  .dependsOn(scheduler % "compile->compile;test->test")

val schema = inputKey[Unit]("Generate the Avro schema file for the Schedule schema.")

lazy val avro = (project in file("avro"))
  .settings(scala3Settings)
  .settings(libraryDependencies ++= Dependencies.avro)
  .settings(schema := (Compile / run).toTask("").value)
  .settings(buildInfoSettings("uk.sky"))
  .dependsOn(scheduler)

lazy val root = (project in file("."))
  .withId("kafka-message-scheduler")
  .aggregate(scheduler, it, avro)
  .enablePlugins(DockerComposePlugin)
  .settings(Aliases.core)
