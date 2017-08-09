import com.typesafe.sbt.packager.docker.Cmd

val kafkaVersion = "0.10.2.1" // TODO: move to 0.11.0.0 when akka-stream-kafka upgrades
val akkaVersion = "2.5.3"
val kamonVersion = "0.6.7"

val dependencies = Seq(
  "com.typesafe.akka"          %% "akka-actor"                 % akkaVersion,
  "com.typesafe.akka"          %% "akka-stream"                % akkaVersion,
  "com.typesafe.akka"          %% "akka-slf4j"                 % akkaVersion,
  "com.typesafe.akka"          %% "akka-stream-kafka"          % "0.16",
  "com.typesafe.akka"          %% "akka-stream-contrib"        % "0.8",

  "com.typesafe.scala-logging" %% "scala-logging"              % "3.5.0",
  "com.sksamuel.avro4s"        %% "avro4s-core"                % "1.7.0",
  "org.typelevel"              %% "cats"                       % "0.9.0",
  "ch.qos.logback"              % "logback-classic"            % "1.2.3"      % Runtime,
  "com.github.pureconfig"      %% "pureconfig"                 % "0.7.2",
  "org.zalando"                %% "grafter"                    % "2.0.1",

  "io.kamon"                   %% "kamon-jmx"                  % kamonVersion,
  "io.kamon"                   %% "kamon-akka-2.5"             % kamonVersion,
  "io.kamon"                   %% "kamon-core"                 % kamonVersion,

  "org.scalatest"              %% "scalatest"                  % "3.0.1"      % Test,
  "com.typesafe.akka"          %% "akka-testkit"               % akkaVersion  % Test,
  "com.typesafe.akka"          %% "akka-stream-testkit"        % akkaVersion  % Test,
  "net.cakesolutions"          %% "scala-kafka-client-testkit" % kafkaVersion % Test,
  "org.slf4j"                   % "log4j-over-slf4j"           % "1.7.25"     % Test,
  "com.danielasfregola"        %% "random-data-generator"      % "2.1"        % Test,
  "com.47deg"                  %% "scalacheck-toolbox-datetime"% "0.2.2"      % Test,
  "com.miguno.akka"            %% "akka-mock-scheduler"        % "0.5.1"      % Test,
  "org.mockito"                 % "mockito-all"                % "1.10.19"    % Test
)

val commonSettings = Seq(
  organization := "com.sky",
  scalaVersion := "2.12.2",
  libraryDependencies += "com.sksamuel.avro4s" %% "avro4s-core" % "1.7.0"
)

val jmxPort = 9186

val dockerSettings = Seq(
  packageName in Docker := packageName.value,
  dockerBaseImage := "openjdk:8u131-jre-alpine",
  dockerRepository := Some("skyuk"),
  maintainer in Docker := "Sky",
  dockerCommands ++= Seq(
    Cmd("USER", "root"),
    Cmd("RUN", "apk update && apk add bash")
  ),
  dockerExposedPorts in Docker := Seq(jmxPort)
)

val buildInfoSettings = Seq(
  buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
  buildInfoPackage := "com.sky"
)

val jmxSettings = Seq(
  "-Djava.rmi.server.hostname=127.0.0.1",
  s"-Dcom.sun.management.jmxremote.port=$jmxPort",
  s"-Dcom.sun.management.jmxremote.rmi.port=$jmxPort",
  "-Dcom.sun.management.jmxremote.ssl=false",
  "-Dcom.sun.management.jmxremote.local.only=false",
  "-Dcom.sun.management.jmxremote.authenticate=false"
).mkString(" ")

lazy val scheduler = (project in file("scheduler"))
  .enablePlugins(BuildInfoPlugin, JavaAppPackaging, UniversalDeployPlugin, JavaAgent)
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= dependencies,
    resolvers += Resolver.bintrayRepo("cakesolutions", "maven"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    scalacOptions ++= Seq(
      "-language:implicitConversions",
      "-language:postfixOps",
      "-Xfatal-warnings",
      "-Ywarn-dead-code",
      "-encoding", "utf-8"
    ),
    fork in run := true,
    javaAgents += "org.aspectj" % "aspectjweaver" % "1.8.10",
    javaOptions in Universal += jmxSettings,
    buildInfoSettings,
    dockerSettings,
    dependencyOverrides += "org.scalacheck" %% "scalacheck" % "1.13.5"
  ).enablePlugins(DockerPlugin)

val schema = inputKey[Unit]("Generate the Avro schema file for the Schedule schema.")

lazy val avro = (project in file("avro"))
  .settings(commonSettings)
  .settings(schema := (run in Compile).toTask("").value)
  .dependsOn(scheduler % "compile->compile")

lazy val root = (project in file("."))
  .settings(commonSettings)
  .aggregate(scheduler, avro)
  .disablePlugins(DockerPlugin)
