import sbt.*

object Dependencies {

  object Akka {
    private val version = "2.6.19"
    val actor           = "com.typesafe.akka" %% "akka-actor"          % version
    val stream          = "com.typesafe.akka" %% "akka-stream"         % version
    val streamKafka     = "com.typesafe.akka" %% "akka-stream-kafka"   % "3.0.1"
    val slf4j           = "com.typesafe.akka" %% "akka-slf4j"          % version
    val testKit         = "com.typesafe.akka" %% "akka-testkit"        % version % Test
    val streamTestKit   = "com.typesafe.akka" %% "akka-stream-testkit" % version % Test
    val base            = Seq(actor, stream, streamKafka, slf4j)
    val test            = Seq(testKit, streamTestKit)
  }

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
    lazy val kafka = "com.github.fd4s" %% "fs2-kafka" % kafkaVersion
  }

  object Kafka {
    private val version = "3.1.0"
    val kafkaClients    = "org.apache.kafka"  % "kafka-clients" % version
    val kafka           = "org.apache.kafka" %% "kafka"         % version % Test
    val base            = Seq(kafkaClients)
    val test            = Seq(kafka)
  }

  object Kamon {
    private val version = "2.6.6"
    val core            = "io.kamon" %% "kamon-core"       % version
    val akka            = "io.kamon" %% "kamon-akka"       % version
    val prometheus      = "io.kamon" %% "kamon-prometheus" % version
    val all             = Seq(core, akka, prometheus)
  }

  object Monocle {
    lazy val core = "dev.optics" %% "monocle-core" % "3.3.0"
  }

  object PureConfig {
    private val version = "0.17.8"
    // TODO: Remove if not needed in Scala3
    val pureconfig      = "com.github.pureconfig" %% "pureconfig"             % version
    val core            = "com.github.pureconfig" %% "pureconfig-core"        % version
    // TODO: Remove if not needed in Scala3
    val cats            = "com.github.pureconfig" %% "pureconfig-cats"        % version
    val catsEffect      = "com.github.pureconfig" %% "pureconfig-cats-effect" % version
    val allScala2       = Seq(pureconfig, cats)
  }

  object Refined {
    private val version = "0.9.29"
    val refined         = "eu.timepit" %% "refined"            % version
    val pureconfig      = "eu.timepit" %% "refined-pureconfig" % version
    val scalaCheck      = "eu.timepit" %% "refined-scalacheck" % version % Test
    val base            = Seq(refined, pureconfig)
    val test            = Seq(scalaCheck)
  }

  object OpenTelemetry {
    private lazy val version      = "1.45.0"
    private lazy val agentVersion = "2.12.0"

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
    val generic = "com.github.fd4s" %% "vulcan-generic" % version % Test
  }

  val avro4s           = "com.sksamuel.avro4s"        %% "avro4s-core"        % "4.1.2"
  val kafkaTopicLoader = "uk.sky"                     %% "kafka-topic-loader" % "1.5.6"
  val monix            = "io.monix"                   %% "monix-execution"    % "3.4.1"
  val mouse            = "org.typelevel"              %% "mouse"              % "1.3.2"
  val scalaLogging     = "com.typesafe.scala-logging" %% "scala-logging"      % "3.9.5"

  val janino         = "org.codehaus.janino"  % "janino"                   % "3.1.12" % Runtime
  val logbackClassic = "ch.qos.logback"       % "logback-classic"          % "1.5.6"  % Runtime
  val logbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.4"    % Runtime

  val embeddedKafka        = "io.github.embeddedkafka" %% "embedded-kafka"              % "3.4.1"    % Test
  val mockito              = "org.mockito"              % "mockito-core"                % "5.10.0"   % Test
  val randomDataGenerator  = "com.danielasfregola"     %% "random-data-generator"       % "2.9"      % Test
  val scalaCheck           = "org.scalacheck"          %% "scalacheck"                  % "1.17.0"   % Test
  val scalaCheckDatetime   = "com.47deg"               %% "scalacheck-toolbox-datetime" % "0.7.0"    % Test
  val scalaTest            = "org.scalatest"           %% "scalatest"                   % "3.2.18"   % Test
  val scalaTestPlusMockito = "org.scalatestplus"       %% "mockito-3-12"                % "3.2.10.0" % Test

  val core: Seq[ModuleID] =
    Akka.base ++ Cats.base ++ Kafka.base ++ Kamon.all ++ PureConfig.allScala2 ++ Refined.base ++ Seq(
      avro4s,
      kafkaTopicLoader,
      monix,
      scalaLogging
    )

  val runtime: Seq[ModuleID] = Seq(
    janino,
    logbackClassic,
    logbackEncoder
  )

  val test: Seq[ModuleID]      = Akka.test ++ Cats.test ++ Kafka.test ++ Refined.test ++ Seq(
    embeddedKafka,
    mockito,
    randomDataGenerator,
    scalaCheck,
    scalaCheckDatetime,
    scalaTest,
    scalaTestPlusMockito
  )
  val scheduler: Seq[ModuleID] = core ++ runtime ++ test

  val scheduler3: Seq[ModuleID] = Seq(
    Cats.caseInsensitive,
    Cats.caseInsensitiveTesting,
    Cats.effect,
    Cats.effectTestKit,
    Cats.effectTesting,
    Cats.effectTestkitScalatest,
    Cats.log4cats,
    Cats.log4catsSlf4j,
    Fs2.core,
    Fs2.kafka,
    Monocle.core,
    Vulcan.core,
    Vulcan.generic,
    PureConfig.core,
    PureConfig.catsEffect,
    OpenTelemetry.exporterOtlp,
    OpenTelemetry.exporterPrometheus,
    OpenTelemetry.javaAgent,
    OpenTelemetry.sdkAutoconfigure,
    Otel4s.java,
    Otel4s.testkit,
    mouse
  )
}
