import sbt.*

object Dependencies {

  object Cats {
    lazy val core             = "org.typelevel" %% "cats-core"                     % "2.10.0"
    lazy val effect           = "org.typelevel" %% "cats-effect"                   % "3.5.2"
    lazy val log4cats         = "org.typelevel" %% "log4cats-core"                 % "2.6.0"
    lazy val log4catsSlf4j    = "org.typelevel" %% "log4cats-slf4j"                % "2.6.0"
    lazy val effectTesting    = "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test
    lazy val testKit          = "org.typelevel" %% "cats-effect-testkit"           % "3.5.2" % Test
    lazy val testkitScalatest = "org.typelevel" %% "cats-testkit-scalatest"        % "2.1.5" % Test
  }

  object Chimney {
    lazy val chimney = "io.scalaland" %% "chimney" % "0.8.4"
  }

  object Circe {
    private lazy val version = "0.14.6"

    lazy val generic = "io.circe" %% "circe-generic" % version
    lazy val parser  = "io.circe" %% "circe-parser"  % version
  }

  object Fs2 {
    private lazy val version      = "3.9.2"
    private lazy val kafkaVersion = "3.2.0"

    lazy val core        = "co.fs2"          %% "fs2-core"         % version
    lazy val io          = "co.fs2"          %% "fs2-io"           % version
    lazy val kafka       = "com.github.fd4s" %% "fs2-kafka"        % kafkaVersion
    lazy val kafkaVulcan = "com.github.fd4s" %% "fs2-kafka-vulcan" % kafkaVersion
  }

  object Janino {
    val janino = "org.codehaus.janino" % "janino" % "3.1.11" % Runtime
  }

  object Logback {
    lazy val classic = "ch.qos.logback" % "logback-classic" % "1.4.14" % Runtime
  }

  object Logstash {
    lazy val logbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.4" % Runtime
  }

  object Monocle {
    lazy val core = "dev.optics" %% "monocle-core" % "3.2.0"
  }

  object OpenTelemetry {
    private lazy val version      = "1.33.0"
    private lazy val alphaVersion = "1.33.0-alpha"
    private lazy val agentVersion = "1.32.0"

    lazy val exporterOtlp       = "io.opentelemetry" % "opentelemetry-exporter-otlp"       % version      % Runtime
    lazy val exporterPrometheus = "io.opentelemetry" % "opentelemetry-exporter-prometheus" % alphaVersion % Runtime
    lazy val sdkAutoconfigure   =
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % version

    lazy val javaAgent = "io.opentelemetry.javaagent" % "opentelemetry-javaagent" % agentVersion % Runtime
  }

  object Otel4s {
    private lazy val version = "0.3.0"

    lazy val java    = "org.typelevel" %% "otel4s-java"    % version
    lazy val testkit = "org.typelevel" %% "otel4s-testkit" % version % Test
  }

  object PureConfig {
    private lazy val version = "0.17.4"

    lazy val core       = "com.github.pureconfig" %% "pureconfig-core"        % version
    lazy val cats       = "com.github.pureconfig" %% "pureconfig-cats"        % version
    lazy val catsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % version
  }

  object ScalaTest {
    lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.17" % Test
  }

  object TopicLoader {
    lazy val topicLoader = "uk.sky" %% "fs2-kafka-topic-loader" % "0.0.4"
  }

  object Typelevel {
    val caseInsensitive = "org.typelevel" %% "case-insensitive" % "1.4.0"
  }

  object Vulcan {
    private lazy val version = "1.9.0"

    val core    = "com.github.fd4s" %% "vulcan"         % version
    val generic = "com.github.fd4s" %% "vulcan-generic" % version % Test
  }

}
