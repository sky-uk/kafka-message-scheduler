import com.typesafe.sbt.packager.docker.Cmd
import Aliases._
import Release._

val kafkaVersion = "1.1.1"
val akkaVersion = "2.5.17"
val kamonVersion = "1.1.1"

val dependencies = Seq(
  "com.typesafe.akka"          %% "akka-actor"                 % akkaVersion,
  "com.typesafe.akka"          %% "akka-stream"                % akkaVersion,
  "com.typesafe.akka"          %% "akka-slf4j"                 % akkaVersion,
  "com.typesafe.akka"          %% "akka-stream-kafka"          % "0.22",
  "com.typesafe.akka"          %% "akka-stream-contrib"        % "0.9",
  "io.monix"                   %% "monix-execution"            % "3.0.0-RC1",

  "com.typesafe.scala-logging" %% "scala-logging"              % "3.9.0",
  "org.typelevel"              %% "cats-core"                  % "1.4.0",
  "ch.qos.logback"              % "logback-classic"            % "1.2.3"      % Runtime,
  "net.logstash.logback"        % "logstash-logback-encoder"   % "5.2"        % Runtime,
  "org.codehaus.janino"         % "janino"                     % "3.0.10"     % Runtime,
  "com.github.pureconfig"      %% "pureconfig"                 % "0.9.2",

  "io.kamon"                   %% "kamon-prometheus"           % kamonVersion,
  "io.kamon"                   %% "kamon-akka-2.5"             % kamonVersion,
  "io.kamon"                   %% "kamon-core"                 % kamonVersion,
  "io.kamon"                   %% "kamon-system-metrics"       % "1.0.0",
  "io.kamon"                   %% "kamon-jmx-collector"        % "0.1.7",

  "org.apache.kafka"           %% "kafka"                      % kafkaVersion % Test,
  "org.scalatest"              %% "scalatest"                  % "3.0.5"      % Test,
  "com.typesafe.akka"          %% "akka-testkit"               % akkaVersion  % Test,
  "com.typesafe.akka"          %% "akka-stream-testkit"        % akkaVersion  % Test,
  "net.cakesolutions"          %% "scala-kafka-client-testkit" % kafkaVersion % Test,
  "org.slf4j"                   % "log4j-over-slf4j"           % "1.7.25"     % Test,
  "com.danielasfregola"        %% "random-data-generator"      % "2.6"        % Test,
  "com.47deg"                  %% "scalacheck-toolbox-datetime"% "0.2.5"      % Test,
  "org.mockito"                 % "mockito-all"                % "1.10.19"    % Test,
  "org.zalando"                %% "grafter"                    % "2.6.1"      % Test
)

val commonSettings = Seq(
  organization := "com.sky",
  scalaVersion := "2.12.7",
  libraryDependencies += "com.sksamuel.avro4s" %% "avro4s-core" % "2.0.1"
)

lazy val dockerSettings = Seq(
  packageName in Docker := "kafka-message-scheduler",
  dockerBaseImage := "openjdk:8u131-jre-alpine",
  dockerRepository := Some("skyuk"),
  dockerLabels := Map("maintainer" -> "Sky"),
  dockerUpdateLatest := updateLatest.value,
  dockerCommands ++= Seq(
    Cmd("USER", "root"),
    Cmd("RUN", "apk update && apk add bash")
  )
)

def updateLatest = Def.setting {
  if (!version.value.contains("SNAPSHOT")) true
  else false
}

val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "com.sky"
)

lazy val scheduler = (project in file("scheduler"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, UniversalDeployPlugin, JavaAgent, DockerPlugin)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= dependencies,
    dependencyOverrides += "org.scalacheck" %% "scalacheck" % "1.13.5",
    resolvers ++= Seq(
      "segence" at "https://dl.bintray.com/segence/maven-oss-releases/",
      Resolver.bintrayRepo("cakesolutions", "maven")),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full),
    scalacOptions ++= Seq(
      "-language:implicitConversions",
      "-language:postfixOps",
      "-Xfatal-warnings",
      "-Ywarn-dead-code",
      "-deprecation",
      "-encoding", "utf-8"
    ),
    fork in run := true,
    javaAgents += "org.aspectj" % "aspectjweaver" % "1.9.1",
    javaOptions in Universal += "-Dorg.aspectj.tracing.factory=default",
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

lazy val root = (project in file(".")).withId("kafka-message-scheduler")
  .settings(commonSettings)
  .settings(defineCommandAliases)
  .aggregate(scheduler, avro)
  .disablePlugins(ReleasePlugin)
