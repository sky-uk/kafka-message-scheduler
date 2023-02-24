import Release._
import DockerPublish._

ThisBuild / scalafmtOnCompile                              := true
ThisBuild / semanticdbEnabled                              := true
ThisBuild / semanticdbVersion                              := scalafixSemanticdb.revision
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.6.0"

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / excludeLintKeys ++= Set(testCasesJar, composeContainerPauseBeforeTestSeconds)

Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oF")

lazy val IntegrationTest = config("it") extend Test

val commonSettings = Seq(
  organization := "com.sky",
  scalaVersion := "2.13.10"
)

val compilerSettings = Seq(
  // Compiler option not provided by sbt-tpolecat
  scalacOptions += "-Ymacro-annotations",
  tpolecatScalacOptions ~= { opts =>
    opts.filterNot(Set(ScalacOptions.warnValueDiscard))
  }
)

lazy val integrationTestSettings =
  Defaults.itSettings ++ inConfig(IntegrationTest)(scalafixConfigSettings(IntegrationTest)) ++ Seq(
    testCasesPackageTask                   := (IntegrationTest / sbt.Keys.packageBin).value,
    testCasesJar                           := (IntegrationTest / packageBin / artifactPath).value.getAbsolutePath,
    dockerImageCreationTask                := (Docker / publishLocal).value,
    composeContainerPauseBeforeTestSeconds := 45
  )

val buildInfoSettings = Seq(
  buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "com.sky"
)

lazy val scheduler = (project in file("scheduler"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, UniversalDeployPlugin, JavaAgent, DockerPlugin, DockerComposePlugin)
  .settings(commonSettings)
  .settings(compilerSettings)
  .settings(integrationTestSettings)
  .configs(IntegrationTest)
  .settings(
    libraryDependencies ++= Dependencies.all,
    addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full),
    run / fork               := true,
    Test / fork              := true,
    javaAgents += "io.kamon"  % "kanela-agent" % "1.0.17",
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
  .settings(dockerImageCreationTask := (scheduler / Docker / publishLocal).value)
  .aggregate(scheduler, avro)
  .enablePlugins(DockerComposePlugin)
  .disablePlugins(ReleasePlugin)
