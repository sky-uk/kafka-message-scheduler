import Release.*
import DockerPublish.*
import org.typelevel.scalacoptions.ScalacOptions

ThisBuild / organization := "com.sky"

ThisBuild / scalafmtOnCompile := true
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

Global / onChangedBuildSource := ReloadOnSourceChanges

val scala2Settings = Seq(
  scalaVersion             := "2.13.15",
  tpolecatScalacOptions ++= Set(
    ScalacOptions.other("-Ymacro-annotations"),
    ScalacOptions.source3
  ),
  tpolecatExcludeOptions ++= Set(
    ScalacOptions.warnNonUnitStatement,
    ScalacOptions.warnValueDiscard
  ),
  run / fork               := true,
  Test / fork              := true,
  Test / parallelExecution := false
)

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
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, UniversalDeployPlugin, JavaAgent, DockerPlugin)
  .settings(scala2Settings)
  .settings(
    libraryDependencies ++= Dependencies.scheduler,
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full),
    javaAgents += "io.kamon" % "kanela-agent" % "1.0.18",
    buildInfoSettings("com.sky"),
    dockerSettings,
    releaseSettings
  )

lazy val scheduler3 = (project in file("scheduler-3"))
  .enablePlugins(JavaAgent, DockerPlugin, JavaAppPackaging, BuildInfoPlugin)
  .settings(scala3Settings)
  .settings(
    libraryDependencies ++= Dependencies.scheduler3,
    buildInfoSettings("uk.sky"),
    scalafixConfig := Some((ThisBuild / baseDirectory).value / ".scalafix3.conf"),
    scalafmtConfig := (ThisBuild / baseDirectory).value / ".scalafmt3.conf"
  )

val schema = inputKey[Unit]("Generate the Avro schema file for the Schedule schema.")

lazy val avro = (project in file("avro"))
  .settings(scala2Settings)
  .settings(libraryDependencies += Dependencies.avro4s)
  .settings(schema := (Compile / run).toTask("").value)
  .dependsOn(scheduler % "compile->compile")
  .disablePlugins(ReleasePlugin)

lazy val root = (project in file("."))
  .withId("kafka-message-scheduler")
  .settings(dockerImageCreationTask := (scheduler / Docker / publishLocal).value)
  .aggregate(scheduler, scheduler3, avro)
  .enablePlugins(DockerComposePlugin)
  .disablePlugins(ReleasePlugin)
