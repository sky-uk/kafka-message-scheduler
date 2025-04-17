import sbt.*

object Dependencies {

  object Cats {
    private val version           = "2.7.0"
    private val catsEffectVersion = "3.5.7"
    private val log4catsVersion   = "2.7.0"

    lazy val effectTestKit          = "org.typelevel"    %% "cats-effect-testkit"           % catsEffectVersion % Test
    lazy val effectTesting          = "org.typelevel"    %% "cats-effect-testing-scalatest" % "1.6.0"           % Test
    lazy val effectTestkitScalatest = "org.typelevel"    %% "cats-testkit-scalatest"        % "2.1.5"           % Test
    lazy val caseInsensitive        = "org.typelevel"    %% "case-insensitive"              % "1.4.2"
    lazy val caseInsensitiveTesting = "org.typelevel"    %% "case-insensitive-testing"      % "1.4.2"
    lazy val core                   = "org.typelevel"    %% "cats-core"                     % version
    lazy val effect                 = "org.typelevel"    %% "cats-effect"                   % catsEffectVersion
    lazy val log4cats               = "org.typelevel"    %% "log4cats-core"                 % log4catsVersion
    lazy val log4catsSlf4j          = "org.typelevel"    %% "log4cats-slf4j"                % log4catsVersion
    lazy val scalatest              = "com.ironcorelabs" %% "cats-scalatest"                % "3.1.1"           % Test
    lazy val testKit                = "org.typelevel"    %% "cats-testkit"                  % version           % Test
    lazy val base                   = Seq(core)
    lazy val test                   = Seq(testKit, scalatest)
  }

  object Fs2 {
    private lazy val version      = "3.11.0"
    private lazy val kafkaVersion = "3.6.0"

    lazy val core  = "co.fs2"          %% "fs2-core"  % version
    lazy val io    = "co.fs2"          %% "fs2-io"    % version
    lazy val kafka = "com.github.fd4s" %% "fs2-kafka" % kafkaVersion
  }

  object Kafka {
    private val version = "3.1.0"
    val kafkaClients    = "org.apache.kafka"  % "kafka-clients" % version
    val kafka           = "org.apache.kafka" %% "kafka"         % version % Test
    val base            = Seq(kafkaClients)
    val test            = Seq(kafka)
  }

  object Logstash {
    lazy val logbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "8.0" % Runtime
  }

  object Monocle {
    lazy val core = "dev.optics" %% "monocle-core" % "3.3.0"
  }

  object PureConfig {
    private val version = "0.17.8"
    val core            = "com.github.pureconfig" %% "pureconfig-core"        % version
    val catsEffect      = "com.github.pureconfig" %% "pureconfig-cats-effect" % version
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

  object Vulcan {
    private lazy val version = "1.11.1"

    val core    = "com.github.fd4s" %% "vulcan"         % version
    val generic = "com.github.fd4s" %% "vulcan-generic" % version
  }

  object Circe {
    private lazy val version = "0.14.10"

    lazy val generic = "io.circe" %% "circe-generic" % version
    lazy val parser  = "io.circe" %% "circe-parser"  % version
  }

  val avro4s =
    "com.sksamuel.avro4s" %% "avro4s-core" % "5.0.14" // todo: this will need to be depricated in the issue #126
  val chimney        = "io.scalaland"  %% "chimney"                % "1.5.0"
  val fs2TopicLoader = "uk.sky"        %% "fs2-kafka-topic-loader" % "0.1.0"
  val mouse          = "org.typelevel" %% "mouse"                  % "1.3.2"

  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.5.15" % Runtime

  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.18" % Test

  val core: Seq[ModuleID] = Seq(
    Cats.caseInsensitive,
    Cats.caseInsensitiveTesting,
    Cats.core,
    Cats.effect,
    Cats.effectTestKit,
    Cats.effectTesting,
    Cats.effectTestkitScalatest,
    Cats.log4cats,
    Cats.log4catsSlf4j,
    Cats.testKit,
    chimney,
    Circe.generic,
    Circe.parser,
    Fs2.core,
    Fs2.kafka,
    Fs2.io,
    fs2TopicLoader,
    logbackClassic,
    Logstash.logbackEncoder,
    Monocle.core,
    mouse,
    Vulcan.core,
    Vulcan.generic,
    PureConfig.catsEffect,
    PureConfig.core,
    OpenTelemetry.exporterOtlp,
    OpenTelemetry.exporterPrometheus,
    OpenTelemetry.sdkAutoconfigure,
    Otel4s.java,
    Otel4s.testkit
  )

  val it: Seq[ModuleID] = Seq(
    Cats.effect,
    Cats.effectTesting,
    Cats.core,
    Circe.generic,
    Circe.parser,
    Fs2.core,
    Fs2.kafka,
    logbackClassic,
    scalaTest
  )
}
