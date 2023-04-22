import sbt._

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
    private val version = "2.7.0"
    val core            = "org.typelevel"    %% "cats-core"      % version
    val testKit         = "org.typelevel"    %% "cats-testkit"   % version % Test
    val scalatest       = "com.ironcorelabs" %% "cats-scalatest" % "3.1.1" % Test
    val base            = Seq(core)
    val test            = Seq(testKit, scalatest)
  }

  object Kafka {
    private val version = "3.1.0"
    val kafkaClients    = "org.apache.kafka"  % "kafka-clients" % version
    val kafka           = "org.apache.kafka" %% "kafka"         % version % Test
    val base            = Seq(kafkaClients)
    val test            = Seq(kafka)
  }

  object Kamon {
    private val version = "2.5.1"
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
    val scalaCheck      = "eu.timepit" %% "refined-scalacheck" % version % Test
    val base            = Seq(refined, pureconfig)
    val test            = Seq(scalaCheck)
  }

  val avro4s           = "com.sksamuel.avro4s"        %% "avro4s-core"        % "4.1.1"
  val kafkaTopicLoader = "uk.sky"                     %% "kafka-topic-loader" % "1.5.6"
  val monix            = "io.monix"                   %% "monix-execution"    % "3.4.1"
  val scalaLogging     = "com.typesafe.scala-logging" %% "scala-logging"      % "3.9.5"

  val janino         = "org.codehaus.janino"  % "janino"                   % "3.1.9" % Runtime
  val logbackClassic = "ch.qos.logback"       % "logback-classic"          % "1.4.7" % Runtime
  val logbackEncoder = "net.logstash.logback" % "logstash-logback-encoder" % "7.3"   % Runtime

  val embeddedKafka        = "io.github.embeddedkafka" %% "embedded-kafka"              % "3.4.0.1"  % Test
  val mockito              = "org.mockito"              % "mockito-core"                % "5.3.0"    % Test
  val randomDataGenerator  = "com.danielasfregola"     %% "random-data-generator"       % "2.9"      % Test
  val scalaCheck           = "org.scalacheck"          %% "scalacheck"                  % "1.17.0"   % Test
  val scalaCheckDatetime   = "com.47deg"               %% "scalacheck-toolbox-datetime" % "0.7.0"    % Test
  val scalaTest            = "org.scalatest"           %% "scalatest"                   % "3.2.15"   % Test
  val scalaTestPlusMockito = "org.scalatestplus"       %% "mockito-3-12"                % "3.2.10.0" % Test

  val core: Seq[ModuleID]    = Akka.base ++ Cats.base ++ Kafka.base ++ Kamon.all ++ PureConfig.all ++ Refined.base ++ Seq(
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
  val test: Seq[ModuleID]    = Akka.test ++ Cats.test ++ Kafka.test ++ Refined.test ++ Seq(
    embeddedKafka,
    mockito,
    randomDataGenerator,
    scalaCheck,
    scalaCheckDatetime,
    scalaTest,
    scalaTestPlusMockito
  )
  val all: Seq[sbt.ModuleID] = core ++ runtime ++ test
}
