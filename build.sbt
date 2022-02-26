import com.typesafe.sbt.packager.docker.Cmd
import Aliases._
import Release._

ThisBuild / scalafmtOnCompile                              := true
ThisBuild / semanticdbEnabled                              := true
ThisBuild / semanticdbVersion                              := scalafixSemanticdb.revision
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

val commonSettings = Seq(
  organization                                 := "com.sky",
  scalaVersion                                 := "2.12.10",
  libraryDependencies += "com.sksamuel.avro4s" %% "avro4s-core" % "4.0.12"
)

lazy val dockerSettings = Seq(
  Docker / packageName := "kafka-message-scheduler",
  dockerBaseImage      := "alpine:3.13.2",
  dockerRepository     := Some("skyuk"),
  dockerLabels         := Map("maintainer" -> "Sky"),
  dockerUpdateLatest   := true,
  dockerCommands ++= Seq(
    Cmd("USER", "root"),
    Cmd("RUN", "apk add --no-cache bash eudev openjdk11-jre")
  )
)

val buildInfoSettings = Seq(
  buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "com.sky"
)

lazy val scheduler = (project in file("scheduler"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, UniversalDeployPlugin, JavaAgent, DockerPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Dependencies.all,
    dependencyOverrides ++= Seq(
      Dependencies.Kafka.kafkaClients,
      Dependencies.scalaCheck
    ),
    resolvers ++= Seq(
      "jitpack" at "https://jitpack.io"
    ),
    addCompilerPlugin("org.scalamacros" % "paradise"       % "2.1.1" cross CrossVersion.full),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.10"),
    scalacOptions ++= Seq(
      "-language:implicitConversions",
      "-language:postfixOps",
      "-Xfatal-warnings",
      "-Ywarn-dead-code",
      "-Ywarn-unused",
      "-deprecation",
      "-Ypartial-unification",
      "-encoding",
      "utf-8",
      "-feature",
      "-Ywarn-unused"
    ),
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
  .settings(schema := (Compile / run).toTask("").value)
  .settings(
    scalacOptions ++= Seq(
      "-Ywarn-unused"
    )
  )
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
