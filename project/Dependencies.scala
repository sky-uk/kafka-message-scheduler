import sbt.*

object Dependencies {

  object Cats {
    lazy val core          = "org.typelevel" %% "cats-core"                     % "2.10.0"
    lazy val effect        = "org.typelevel" %% "cats-effect"                   % "3.5.2"
    lazy val log4cats      = "org.typelevel" %% "log4cats-core"                 % "2.6.0"
    lazy val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j"                % "2.6.0"
    lazy val effectTesting = "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test
  }

  object Circe {
    private lazy val version = "0.14.6"

    lazy val generic = "io.circe" %% "circe-generic" % version
    lazy val parser  = "io.circe" %% "circe-parser"  % version
  }

  object EmbeddedKafka {
    lazy val embeddedKafka = "io.github.embeddedkafka" %% "embedded-kafka" % "3.6.0" % Test
  }

  object Fs2 {
    private lazy val kafkaVersion = "3.2.0"

    lazy val core        = "co.fs2"          %% "fs2-core"         % "3.9.2"
    lazy val kafka       = "com.github.fd4s" %% "fs2-kafka"        % kafkaVersion
    lazy val kafkaVulcan = "com.github.fd4s" %% "fs2-kafka-vulcan" % kafkaVersion
  }

  object Logback {
    lazy val classic = "ch.qos.logback" % "logback-classic" % "1.4.11" % Runtime
  }

  object OpenTelemetry {
    private lazy val version      = "1.30.1"
    private lazy val alphaVersion = "1.30.1-alpha"
    private lazy val agentVersion = "1.31.0"

    lazy val exporterOtlp       = "io.opentelemetry" % "opentelemetry-exporter-otlp"       % version      % Runtime
    lazy val exporterPrometheus = "io.opentelemetry" % "opentelemetry-exporter-prometheus" % alphaVersion % Runtime
    lazy val sdkAutoconfigure   =
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % version

    lazy val javaAgent = "io.opentelemetry.javaagent" % "opentelemetry-javaagent" % agentVersion % Runtime
  }

  object Otel4s {
    private lazy val version = "0.3-edcb397-SNAPSHOT"

    lazy val java    = "org.typelevel" %% "otel4s-java"    % version
    lazy val testkit = "org.typelevel" %% "otel4s-testkit" % version % Test
  }

  object ScalaTest {
    lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.17" % Test
  }

  object Vulcan {
    private lazy val version = "1.9.0"

    val core    = "com.github.fd4s" %% "vulcan"         % version
    val generic = "com.github.fd4s" %% "vulcan-generic" % version // TODO - not used
  }

}
