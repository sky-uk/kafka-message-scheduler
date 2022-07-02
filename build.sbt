import com.typesafe.sbt.packager.docker.Cmd
import Aliases._

ThisBuild / scalafmtOnCompile                              := true
ThisBuild / semanticdbEnabled                              := true
ThisBuild / semanticdbVersion                              := scalafixSemanticdb.revision
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"
ThisBuild / dynverSeparator                                := "-"

Global / onChangedBuildSource := ReloadOnSourceChanges

Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oF")

val commonSettings = Seq(
  organization := "com.sky",
  scalaVersion := "2.13.8"
)

val compilerSettings = Seq(
  // Compiler option not provided by sbt-tpolecat
  scalacOptions += "-Ymacro-annotations",
  tpolecatScalacOptions ~= { opts =>
    opts.filterNot(Set(ScalacOptions.warnValueDiscard))
  }
)

lazy val dockerSettings = Seq(
  Docker / packageName := "sky-uk/kafka-message-scheduler",
  dockerBaseImage      := "eclipse-temurin:17-jdk-alpine",
  dockerRepository     := Some("ghcr.io"),
  dockerLabels         := Map("maintainer" -> "Sky"),
  dockerAliases ++= {
    lazy val dockerTag = (tag: String) => Seq(dockerAlias.value.withTag(Some(tag)))
    if (isSnapshot.value) dockerTag("snapshot") else dockerTag("latest")
  },
  dockerCommands ++= Seq(
    Cmd("USER", "root"),
    Cmd("RUN", "apk add --no-cache bash")
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
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
    run / fork               := true,
    Test / fork              := true,
    javaAgents += "io.kamon"  % "kanela-agent" % "1.0.14",
    buildInfoSettings,
    dockerSettings,
    Test / parallelExecution := false
  )

val schema = inputKey[Unit]("Generate the Avro schema file for the Schedule schema.")

lazy val avro = (project in file("avro"))
  .settings(commonSettings)
  .settings(compilerSettings)
  .settings(libraryDependencies += Dependencies.avro4s)
  .settings(schema := (Compile / run).toTask("").value)
  .dependsOn(scheduler % "compile->compile")

lazy val root = (project in file("."))
  .withId("kafka-message-scheduler")
  .settings(commonSettings)
  .settings(defineCommandAliases)
  .settings(dockerImageCreationTask := (scheduler / Docker / publishLocal).value)
  .aggregate(scheduler, avro)
  .enablePlugins(DockerComposePlugin)
