import Aliases._
import Release._
import com.typesafe.sbt.packager.docker.Cmd

ThisBuild / scalafmtOnCompile                              := true
ThisBuild / semanticdbEnabled                              := true
ThisBuild / semanticdbVersion                              := scalafixSemanticdb.revision
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

Global / onChangedBuildSource := ReloadOnSourceChanges

Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oF")

val commonSettings = Seq(
  organization := "com.sky",
  scalaVersion := "2.13.8"
)

// Everything else is provided by tpolecat
val compilerSettings = Seq(
  scalacOptions += "-Ymacro-annotations",
  scalacOptions -= "-Wvalue-discard"
)

lazy val dockerSettings = Seq(
  Docker / packageName := "kafka-message-scheduler",
  dockerBaseImage      := "alpine:3.15.0",
  dockerRepository     := Some("skyuk"),
  dockerLabels         := Map("maintainer" -> "Sky"),
  dockerUpdateLatest   := true,
  dockerCommands ++= Seq(
    Cmd("USER", "root"),
    Cmd("RUN", "apk add --no-cache bash openjdk17-jre")
  )
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
    dependencyOverrides ++= Seq(
      Dependencies.Kafka.kafkaClients,
      Dependencies.scalaCheck
    ),
    resolvers += "jitpack" at "https://jitpack.io",
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
    run / fork               := true,
    Test / fork              := true,
    javaAgents += "io.kamon"  % "kanela-agent" % "1.0.14",
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
