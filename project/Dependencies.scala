import sbt.*

object Dependencies {

  object Cats {
    private lazy val catsEffectVersion = "3.5.7"
    private lazy val log4sVersion      = "2.7.0"

    lazy val core             = "org.typelevel" %% "cats-core"                     % "2.12.0"
    lazy val effect           = "org.typelevel" %% "cats-effect"                   % catsEffectVersion
    lazy val log4cats         = "org.typelevel" %% "log4cats-core"                 % log4sVersion
    lazy val log4catsSlf4j    = "org.typelevel" %% "log4cats-slf4j"                % log4sVersion
    lazy val effectTesting    = "org.typelevel" %% "cats-effect-testing-scalatest" % "1.6.0"           % Test
    lazy val testKit          = "org.typelevel" %% "cats-effect-testkit"           % catsEffectVersion % Test
    lazy val testkitScalatest = "org.typelevel" %% "cats-testkit-scalatest"        % "2.1.5"           % Test
  }

  object Chimney {
    private lazy val version = "1.5.0"

    lazy val chimney = "io.scalaland" %% "chimney" % version
  }

  object Circe {
    private lazy val version = "0.14.10"

    lazy val generic = "io.circe" %% "circe-generic" % version
    lazy val parser  = "io.circe" %% "circe-parser"  % version
  }

  object Decline {
    private lazy val version = "2.4.1"

    lazy val core   = "com.monovore" %% "decline"        % version
    lazy val effect = "com.monovore" %% "decline-effect" % version
  }

  object Fs2 {
    private lazy val version      = "3.11.0"
    private lazy val kafkaVersion = "3.6.0"

    lazy val core  = "co.fs2"          %% "fs2-core"  % version
    lazy val io    = "co.fs2"          %% "fs2-io"    % version
    lazy val kafka = "com.github.fd4s" %% "fs2-kafka" % kafkaVersion
  }

  object Janino {
    val janino = "org.codehaus.janino" % "janino" % "3.1.12" % Runtime
  }

  object Logback {
    lazy val classic = "ch.qos.logback" % "logback-classic" % "1.5.15" % Runtime
  }

  object Logstash {
    lazy val logbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "8.0" % Runtime
  }

  object Monocle {
    lazy val core = "dev.optics" %% "monocle-core" % "3.3.0"
  }

  object OpenTelemetry {
    private lazy val version      = "1.45.0"
    private lazy val agentVersion = "2.11.0"

    lazy val exporterOtlp       = "io.opentelemetry" % "opentelemetry-exporter-otlp"               % version           % Runtime
    lazy val exporterPrometheus = "io.opentelemetry" % "opentelemetry-exporter-prometheus"         % s"$version-alpha" % Runtime
    lazy val sdkAutoconfigure   = "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % version           % Runtime

    lazy val javaAgent = "io.opentelemetry.javaagent" % "opentelemetry-javaagent" % agentVersion % Runtime
  }

  object Otel4s {
    private lazy val version = "0.11.2"

    lazy val java    = "org.typelevel" %% "otel4s-oteljava"         % version
    lazy val testkit = "org.typelevel" %% "otel4s-oteljava-testkit" % version % Test
  }

  object PureConfig {
    private lazy val version = "0.17.8"

    lazy val core       = "com.github.pureconfig" %% "pureconfig-core"        % version
    lazy val catsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % version
  }

  object ScalaTest {
    lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.19" % Test
  }

  object TopicLoader {
    lazy val topicLoader = "uk.sky" %% "fs2-kafka-topic-loader" % "0.1.0"
  }

  object Typelevel {
    val caseInsensitive        = "org.typelevel" %% "case-insensitive"         % "1.4.2"
    val caseInsensitiveTesting = "org.typelevel" %% "case-insensitive-testing" % "1.4.2" % Test
    val mouse                  = "org.typelevel" %% "mouse"                    % "1.3.2"
  }

  object Vulcan {
    private lazy val version = "1.11.1"

    val core    = "com.github.fd4s" %% "vulcan"         % version
    val generic = "com.github.fd4s" %% "vulcan-generic" % version % Test
  }

}
