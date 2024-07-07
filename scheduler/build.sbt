import Dependencies.*

import scala.sys.process.Process

enablePlugins(JavaAgent, DockerPlugin, JavaAppPackaging, BuildInfoPlugin)

libraryDependencies ++= Seq(
  Cats.core,
  Cats.effect,
  Cats.effectTesting,
  Cats.log4cats,
  Cats.log4catsSlf4j,
  Cats.testKit,
  Cats.testkitScalatest,
  Chimney.chimney,
  Circe.generic,
  Circe.parser,
  Fs2.core,
  Fs2.kafka,
  Fs2.kafkaVulcan,
  Janino.janino,
  Logback.classic,
  Logstash.logbackEncoder,
  Monocle.core,
  OpenTelemetry.exporterOtlp,
  OpenTelemetry.exporterPrometheus,
  OpenTelemetry.sdkAutoconfigure,
  Otel4s.java,
  Otel4s.testkit,
  PureConfig.cats,
  PureConfig.catsEffect,
  PureConfig.core,
  ScalaTest.scalaTest,
  TopicLoader.topicLoader,
  Typelevel.caseInsensitive,
  Typelevel.caseInsensitiveTesting,
  Typelevel.mouse,
  Vulcan.core,
  Vulcan.generic
)

resolvers ++= Resolver.sonatypeOssRepos("snapshots") ++ Seq(
  "confluent" at "https://packages.confluent.io/maven",
  "jitpack" at "https://jitpack.io"
)

run / fork  := true
Test / fork := true

javaOptions += "-Dotel.java.global-autoconfigure.enabled=true"
javaAgents += OpenTelemetry.javaAgent

// Docker settings - TODO move these somewhere better
lazy val ensureDockerBuildx    = taskKey[Unit]("Ensure that docker buildx configuration exists")
lazy val dockerBuildWithBuildx = taskKey[Unit]("Build docker images using buildx")

ensureDockerBuildx := {
  if (Process("docker buildx inspect multi-arch-builder").! == 1) {
    Process("docker buildx create --use --name multi-arch-builder", baseDirectory.value).!
  }
}

dockerBuildWithBuildx := {
  streams.value.log("Building and pushing image with Buildx")
  dockerAliases.value.foreach { alias =>
    Process(
      "docker buildx build --platform=linux/arm64,linux/amd64 --push -t " +
        alias + " .",
      baseDirectory.value / "target" / "docker" / "stage"
    ).!
  }
}

Docker / publish := Def
  .sequential(
    Docker / publishLocal,
    ensureDockerBuildx,
    dockerBuildWithBuildx
  )
  .value
