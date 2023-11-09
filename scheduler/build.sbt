import Dependencies.*

enablePlugins(JavaAgent, DockerPlugin, JavaAppPackaging)

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
  Logback.classic,
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
  Vulcan.core
)

resolvers ++= Resolver.sonatypeOssRepos("snapshots") ++ Seq(
  "confluent" at "https://packages.confluent.io/maven",
  "jitpack" at "https://jitpack.io"
)

run / fork  := true
Test / fork := true

javaOptions += "-Dotel.java.global-autoconfigure.enabled=true"
javaAgents += OpenTelemetry.javaAgent
