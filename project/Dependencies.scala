import sbt._

object Dependencies {

  object Akka {
    private val version = "2.6.18"
    val actor           = "com.typesafe.akka" %% "akka-actor"          % version
    val stream          = "com.typesafe.akka" %% "akka-stream"         % version
    val streamKafka     = "com.typesafe.akka" %% "akka-stream-kafka"   % "3.0.0"
    val slf4j           = "com.typesafe.akka" %% "akka-slf4j"          % version
    val testKit         = "com.typesafe.akka" %% "akka-testkit"        % version % Test
    val streamTestKit   = "com.typesafe.akka" %% "akka-stream-testkit" % version % Test
    val base            = Seq(actor, stream, streamKafka, slf4j)
    val test            = Seq(testKit, streamTestKit)
  }

  object Cats {
    private val version = "2.7.0"
    val core            = "org.typelevel" %% "cats-core"    % version
    val testKit         = "org.typelevel" %% "cats-testkit" % version
    val all             = Seq(core, testKit)
  }

  object Kafka {
    private val version = "3.1.0"
    val kafkaClients    = "org.apache.kafka"  % "kafka-clients" % version
    val kafka           = "org.apache.kafka" %% "kafka"         % version % Test
    val base            = Seq(kafkaClients)
    val test            = Seq(kafka)

  }

  object Kamon {
    private val version = "2.5.0"
    val core            = "io.kamon" %% "kamon-core"       % version
    val akka            = "io.kamon" %% "kamon-akka"       % version
    val prometheus      = "io.kamon" %% "kamon-prometheus" % version
    val all             = Seq(core, akka, prometheus)
  }

  object PureConfig {
    private val version = "0.17.1"
    val pureconfig      = "com.github.pureconfig" %% "pureconfig"      % version
    val cats            = "com.github.pureconfig" %% "pureconfig-cats" % version
    val all             = Seq(pureconfig, cats)
  }

  object Refined {
    private val version = "0.9.28"
    val refined         = "eu.timepit" %% "refined"            % version
    val pureconfig      = "eu.timepit" %% "refined-pureconfig" % version
    val scalaCheck      = "eu.timepit" %% "refined-scalacheck" % version
    val all             = Seq(refined, pureconfig, scalaCheck)
  }

  val kafkaTopicLoader = "uk.sky"                     %% "kafka-topic-loader" % "1.5.5"
  val monix            = "io.monix"                   %% "monix-execution"    % "3.4.0"
  val scalaLogging     = "com.typesafe.scala-logging" %% "scala-logging"      % "3.9.4"
  val scalaCheck       = "org.scalacheck"             %% "scalacheck"         % "1.15.4"

  val logbackClassic = "ch.qos.logback"       % "logback-classic"          % "1.2.11" % Runtime
  val logbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.0.1"  % Runtime
  val janino         = "org.codehaus.janino"  % "janino"                   % "3.1.6"  % Runtime

  val scalaTest            = "org.scalatest"           %% "scalatest"                   % "3.2.11"   % Test
  val randomDataGenerator  = "com.danielasfregola"     %% "random-data-generator"       % "2.9"      % Test
  val scalaCheckDatetime   = "com.47deg"               %% "scalacheck-toolbox-datetime" % "0.6.0"    % Test
  val scalaTestPlusMockito = "org.scalatestplus"       %% "mockito-3-12"                % "3.2.10.0" % Test
  val mockito              = "org.mockito"              % "mockito-core"                % "4.4.0"    % Test
  val embeddedKafka        = "io.github.embeddedkafka" %% "embedded-kafka"              % "3.1.0"    % Test

  val core: Seq[ModuleID]    = Akka.base ++ Cats.all ++ Kamon.all ++ PureConfig.all ++ Refined.all ++ Seq(
    kafkaTopicLoader,
    monix,
    scalaLogging
  )
  val runtime: Seq[ModuleID] = Seq(
    logbackClassic,
    logbackEncoder,
    janino
  )
  val test: Seq[ModuleID]    = Akka.test ++ Kafka.test ++ Seq(
    scalaTest,
    scalaTestPlusMockito,
    randomDataGenerator,
    scalaCheckDatetime,
    mockito,
    embeddedKafka
  )
  val all: Seq[sbt.ModuleID] = core ++ runtime ++ test
}
