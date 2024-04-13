import sbt.*

object Dependencies {

  object Cats {
    private lazy val catsEffectVersion = "3.5.4"
    private lazy val log4sVersion      = "2.6.0"

    lazy val core             = "org.typelevel" %% "cats-core"                     % "2.10.0"
    lazy val effect           = "org.typelevel" %% "cats-effect"                   % catsEffectVersion
    lazy val log4cats         = "org.typelevel" %% "log4cats-core"                 % log4sVersion
    lazy val log4catsSlf4j    = "org.typelevel" %% "log4cats-slf4j"                % log4sVersion
    lazy val effectTesting    = "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0"           % Test
    lazy val testKit          = "org.typelevel" %% "cats-effect-testkit"           % catsEffectVersion % Test
    lazy val testkitScalatest = "org.typelevel" %% "cats-testkit-scalatest"        % "2.1.5"           % Test
  }

  object Chimney {
    lazy val chimney = "io.scalaland" %% "chimney" % "0.8.5"
  }

  object Circe {
    private lazy val version = "0.14.6"

    lazy val generic = "io.circe" %% "circe-generic" % version
    lazy val parser  = "io.circe" %% "circe-parser"  % version
  }

  object Fs2 {
    private lazy val version      = "3.10.2"
    private lazy val kafkaVersion = "3.4.0"

    lazy val core        = "co.fs2"          %% "fs2-core"         % version
    lazy val io          = "co.fs2"          %% "fs2-io"           % version
    lazy val kafka       = "com.github.fd4s" %% "fs2-kafka"        % kafkaVersion
    lazy val kafkaVulcan = "com.github.fd4s" %% "fs2-kafka-vulcan" % kafkaVersion
  }

  object Janino {
    val janino = "org.codehaus.janino" % "janino" % "3.1.12" % Runtime
  }

  object Logback {
    lazy val classic = "ch.qos.logback" % "logback-classic" % "1.5.5" % Runtime
  }

  object Logstash {
    lazy val logbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.4" % Runtime
  }

  object Monocle {
    lazy val core = "dev.optics" %% "monocle-core" % "3.2.0"
  }

  object Netty {
    lazy val grpc = "io.grpc" % "grpc-netty-shaded" % scalapb.compiler.Version.grpcJavaVersion
  }

  object OpenTelemetry {
    private lazy val version      = "1.37.0"
    private lazy val alphaVersion = "1.37.0-alpha"
    private lazy val agentVersion = "2.3.0"

    lazy val exporterOtlp       = "io.opentelemetry" % "opentelemetry-exporter-otlp"       % version      % Runtime
    lazy val exporterPrometheus = "io.opentelemetry" % "opentelemetry-exporter-prometheus" % alphaVersion % Runtime
    lazy val sdkAutoconfigure   =
      "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % version

    lazy val javaAgent = "io.opentelemetry.javaagent" % "opentelemetry-javaagent" % agentVersion % Runtime
  }

  object Otel4s {
    private lazy val version = "0.5.0"

    lazy val java    = "org.typelevel" %% "otel4s-oteljava"         % version
    lazy val testkit = "org.typelevel" %% "otel4s-oteljava-testkit" % version % Test
  }

  object PureConfig {
    private lazy val version = "0.17.6"

    lazy val core       = "com.github.pureconfig" %% "pureconfig-core"        % version
    lazy val cats       = "com.github.pureconfig" %% "pureconfig-cats"        % version
    lazy val catsEffect = "com.github.pureconfig" %% "pureconfig-cats-effect" % version
  }

  object ScalaPb {
    val runtime = "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
  }

  object ScalaTest {
    lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18" % Test
  }

  object TopicLoader {
    lazy val topicLoader = "uk.sky" %% "fs2-kafka-topic-loader" % "0.0.5"
  }

  object Typelevel {
    val caseInsensitive = "org.typelevel" %% "case-insensitive"   % "1.4.0"
    val mouse           = "org.typelevel" %% "mouse"              % "1.2.3"
    val scalafix        = "org.typelevel" %% "typelevel-scalafix" % "0.2.0"
  }

  object Vulcan {
    private lazy val version = "1.10.1"

    val core    = "com.github.fd4s" %% "vulcan"         % version
    val generic = "com.github.fd4s" %% "vulcan-generic" % version % Test
  }

}
