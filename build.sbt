import com.typesafe.sbt.packager.docker.Cmd
import Aliases._
import Release._

scalafmtVersion in ThisBuild := "1.2.0"
scalafmtOnCompile in ThisBuild := true

val kafkaVersion      = "2.6.0"
val akkaVersion       = "2.5.23"
val catsVersion       = "1.6.1"
val refinedVersion    = "0.9.8"
val pureConfigVersion = "0.11.1"
val kamonVersion      = "2.2.2"

val dependencies = Seq(
  "com.github.sky-uk"          % "kafka-topic-loader"           % "v1.3.2",
  "com.typesafe.akka"          %% "akka-actor"                  % akkaVersion,
  "com.typesafe.akka"          %% "akka-stream"                 % akkaVersion,
  "com.typesafe.akka"          %% "akka-slf4j"                  % akkaVersion,
  "com.typesafe.akka"          %% "akka-stream-kafka"           % "1.0.4",
  "com.typesafe.akka"          %% "akka-stream-contrib"         % "0.10",
  "io.monix"                   %% "monix-execution"             % "3.0.0-RC3",
  "com.typesafe.scala-logging" %% "scala-logging"               % "3.9.2",
  "org.typelevel"              %% "cats-core"                   % catsVersion,
  "org.typelevel"              %% "cats-testkit"                % catsVersion,
  "ch.qos.logback"             % "logback-classic"              % "1.2.3" % Runtime,
  "net.logstash.logback"       % "logstash-logback-encoder"     % "6.1" % Runtime,
  "org.codehaus.janino"        % "janino"                       % "3.0.13" % Runtime,
  "com.github.pureconfig"      %% "pureconfig"                  % pureConfigVersion,
  "com.github.pureconfig"      %% "pureconfig-cats"             % pureConfigVersion,
  "io.kamon"                   %% "kamon-core"                  % kamonVersion,
  "io.kamon"                   %% "kamon-akka"                  % kamonVersion,
  "io.kamon"                   %% "kamon-prometheus"            % kamonVersion,
  "eu.timepit"                 %% "refined"                     % refinedVersion,
  "eu.timepit"                 %% "refined-pureconfig"          % refinedVersion,
  "eu.timepit"                 %% "refined-scalacheck"          % refinedVersion,
  "org.apache.kafka"           %% "kafka"                       % kafkaVersion % Test,
  "org.scalatest"              %% "scalatest"                   % "3.0.8" % Test,
  "com.typesafe.akka"          %% "akka-testkit"                % akkaVersion % Test,
  "com.typesafe.akka"          %% "akka-stream-testkit"         % akkaVersion % Test,
  "org.slf4j"                  % "log4j-over-slf4j"             % "1.7.26" % Test,
  "com.danielasfregola"        %% "random-data-generator"       % "2.7" % Test,
  "com.47deg"                  %% "scalacheck-toolbox-datetime" % "0.2.5" % Test,
  "org.mockito"                % "mockito-all"                  % "1.10.19" % Test,
  "org.zalando"                %% "grafter"                     % "2.6.1" % Test,
  "io.github.embeddedkafka"    %% "embedded-kafka"              % "2.6.0" % Test
)

val commonSettings = Seq(
  organization := "com.sky",
  scalaVersion := "2.12.10",
  libraryDependencies += "com.sksamuel.avro4s" %% "avro4s-core" % "2.0.2"
)

lazy val dockerSettings = Seq(
  packageName in Docker := "kafka-message-scheduler",
  dockerBaseImage := "alpine:3.13.2",
  dockerRepository := Some("skyuk"),
  dockerLabels := Map("maintainer" -> "Sky"),
  dockerUpdateLatest := true,
  dockerCommands ++= Seq(
    Cmd("USER", "root"),
    Cmd("RUN", "apk add --no-cache bash eudev openjdk11-jre")
  )
)

val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "com.sky"
)

lazy val scheduler = (project in file("scheduler"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, UniversalDeployPlugin, JavaAgent, DockerPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= dependencies,
    dependencyOverrides ++= Seq("org.apache.kafka" % "kafka-clients" % kafkaVersion,
                                "org.scalacheck"   %% "scalacheck"   % "1.13.5"),
    resolvers ++= Seq(
      "jitpack" at "https://jitpack.io",
      Resolver.bintrayRepo("cakesolutions", "maven")
    ),
    addCompilerPlugin("org.scalamacros" % "paradise"        % "2.1.1" cross CrossVersion.full),
    addCompilerPlugin("org.spire-math"  %% "kind-projector" % "0.9.10"),
    scalacOptions ++= Seq(
      "-language:implicitConversions",
      "-language:postfixOps",
      "-Xfatal-warnings",
      "-Ywarn-dead-code",
      "-Ywarn-unused",
      "-deprecation",
      "-Ypartial-unification",
      "-encoding",
      "utf-8"
    ),
    fork in run := true,
    fork in Test := true,
    javaAgents += "io.kamon" % "kanela-agent" % "1.0.7",
    buildInfoSettings,
    dockerSettings,
    releaseSettings,
    parallelExecution in Test := false
  )

val schema = inputKey[Unit]("Generate the Avro schema file for the Schedule schema.")

lazy val avro = (project in file("avro"))
  .settings(commonSettings)
  .settings(schema := (run in Compile).toTask("").value)
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
