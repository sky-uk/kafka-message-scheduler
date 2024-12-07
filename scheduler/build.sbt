import Dependencies.*

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
  Janino.janino,
  Logback.classic,
  Logstash.logbackEncoder,
  Monocle.core,
  OpenTelemetry.exporterOtlp,
  OpenTelemetry.exporterPrometheus,
  OpenTelemetry.sdkAutoconfigure,
  Otel4s.java,
  Otel4s.testkit,
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
