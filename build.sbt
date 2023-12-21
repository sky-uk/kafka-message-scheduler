import Aliases.*
import Release.*
import DockerPublish.*
import org.typelevel.scalacoptions.ScalacOptions

ThisBuild / scalafmtOnCompile                              := true
ThisBuild / semanticdbEnabled                              := true
ThisBuild / semanticdbVersion                              := scalafixSemanticdb.revision
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

Global / onChangedBuildSource := ReloadOnSourceChanges

Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oF")

val commonSettings = Seq(
  organization := "com.sky",
  scalaVersion := "2.13.10"
)

val compilerSettings = Seq(
  tpolecatScalacOptions ++= Set(ScalacOptions.other("-Ymacro-annotations"), ScalacOptions.source3),
  tpolecatExcludeOptions ++= Set(ScalacOptions.warnNonUnitStatement, ScalacOptions.warnValueDiscard)
)

val buildInfoSettings = Seq(
  buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "com.sky"
)

lazy val scheduler = (project in file("scheduler"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, UniversalDeployPlugin, JavaAgent, DockerPlugin)
  .settings(commonSettings)
  .settings(compilerSettings)
  .settings(
    libraryDependencies ++= Dependencies.all,
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
    run / fork               := true,
    Test / fork              := true,
    javaAgents += "io.kamon"  % "kanela-agent" % "1.0.18",
    buildInfoSettings,
    dockerSettings,
    releaseSettings,
    Test / parallelExecution := false
  )

val schema = inputKey[Unit]("Generate the Avro schema file for the Schedule schema.")

lazy val avro = (project in file("avro"))
  .settings(commonSettings)
  .settings(compilerSettings)
  .settings(libraryDependencies += Dependencies.avro4s)
  .settings(schema := (Compile / run).toTask("").value)
  .dependsOn(scheduler % "compile->compile")
  .disablePlugins(ReleasePlugin)

lazy val root = (project in file("."))
  .withId("kafka-message-scheduler")
  .settings(commonSettings)
  .settings(defineCommandAliases)
  .settings(dockerImageCreationTask := (scheduler / Docker / publishLocal).value)
  .aggregate(scheduler, avro)
  .enablePlugins(DockerComposePlugin)
  .disablePlugins(ReleasePlugin)
