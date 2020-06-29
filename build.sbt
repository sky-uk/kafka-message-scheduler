import com.typesafe.sbt.packager.docker.Cmd
import Aliases._
import Release._

scalafmtVersion in ThisBuild := "1.2.0"
scalafmtOnCompile in ThisBuild := true

val kafkaVersion      = "2.2.0"
val confluentVersion  = "5.2.0"
val akkaVersion       = "2.5.23"
val catsVersion       = "1.6.1"
val refinedVersion    = "0.9.8"
val pureConfigVersion = "0.11.1"

val dependencies = Seq(
  "com.sky"                    %% "kafka-topic-loader"          % "1.3.2",
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
  "io.kamon"                   %% "kamon-prometheus"            % "1.1.2",
  "io.kamon"                   %% "kamon-akka-2.5"              % "1.1.4",
  "io.kamon"                   %% "kamon-core"                  % "1.1.6",
  "io.kamon"                   %% "kamon-jmx-collector"         % "0.1.8",
  "eu.timepit"                 %% "refined"                     % refinedVersion,
  "eu.timepit"                 %% "refined-pureconfig"          % refinedVersion,
  "eu.timepit"                 %% "refined-scalacheck"          % refinedVersion,
  "io.confluent"               % "kafka-schema-registry-client" % confluentVersion,
  "io.confluent"               % "kafka-avro-serializer"        % confluentVersion,
  "org.apache.kafka"           %% "kafka"                       % kafkaVersion % Test,
  "org.scalatest"              %% "scalatest"                   % "3.0.8" % Test,
  "com.typesafe.akka"          %% "akka-testkit"                % akkaVersion % Test,
  "com.typesafe.akka"          %% "akka-stream-testkit"         % akkaVersion % Test,
  "org.slf4j"                  % "log4j-over-slf4j"             % "1.7.26" % Test,
  "com.danielasfregola"        %% "random-data-generator"       % "2.7" % Test,
  "com.47deg"                  %% "scalacheck-toolbox-datetime" % "0.2.5" % Test,
  "org.mockito"                % "mockito-all"                  % "1.10.19" % Test,
  "org.zalando"                %% "grafter"                     % "2.6.1" % Test,
  "io.github.embeddedkafka"    %% "embedded-kafka"              % "2.2.0" % Test
)

val commonSettings = Seq(
  organization := "com.sky",
  scalaVersion := "2.12.10",
  libraryDependencies += "com.sksamuel.avro4s" %% "avro4s-core" % "3.0.9"
)

lazy val dockerSettings = Seq(
  packageName in Docker := "kafka-message-scheduler",
  dockerBaseImage := "openjdk:8u171-jre-alpine",
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
    dependencyOverrides ++= Seq("org.apache.kafka" % "kafka-clients" % "2.2.0",
                                "org.scalacheck"   %% "scalacheck"   % "1.13.5"),
    resolvers ++= Seq(
      "bintray-sky-uk-oss-maven" at "https://dl.bintray.com/sky-uk/oss-maven",
      "segence" at "https://dl.bintray.com/segence/maven-oss-releases/",
      "confluent-release" at "https://packages.confluent.io/maven/",
      Resolver.bintrayRepo("cakesolutions", "maven")
    ),
    addCompilerPlugin("org.scalamacros" % "paradise"        % "2.1.1" cross CrossVersion.full),
    addCompilerPlugin("org.spire-math"  %% "kind-projector" % "0.9.10"),
    scalacOptions ++= Seq(
      "-language:implicitConversions",
      "-language:postfixOps",
      "-language:higherKinds",
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

lazy val root = (project in file("."))
  .withId("kafka-message-scheduler")
  .settings(commonSettings)
  .settings(defineCommandAliases)
  .settings(dockerImageCreationTask := (publishLocal in Docker).value)
  .aggregate(scheduler, avro)
  .enablePlugins(DockerComposePlugin)
  .disablePlugins(ReleasePlugin)
