import Dependencies.*

enablePlugins(JavaAgent, DockerComposePlugin, DockerPlugin, JavaAppPackaging)

libraryDependencies ++= Seq(
  Cats.core,
  Cats.effect,
  Cats.log4cats,
  Cats.log4catsSlf4j,
  Circe.generic,
  Circe.parser,
  Fs2.core,
  Fs2.kafka,
  Logback.classic,
  OpenTelemetry.exporterOtlp,
  OpenTelemetry.exporterPrometheus,
  OpenTelemetry.sdkAutoconfigure,
  Otel4s.java,
  Otel4s.testkit,
  ScalaTest.scalaTest
)

resolvers ++= Resolver.sonatypeOssRepos("snapshots") ++ Seq(
  "confluent" at "https://packages.confluent.io/maven",
  "jitpack" at "https://jitpack.io"
)

javaOptions += "-Dotel.java.global-autoconfigure.enabled=true"
javaAgents += OpenTelemetry.javaAgent
