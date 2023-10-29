import sbt.*

object Dependencies {

  object Cats {
    lazy val core          = "org.typelevel" %% "cats-core"                     % "2.10.0"
    lazy val effect        = "org.typelevel" %% "cats-effect"                   % "3.5.2"
    lazy val log4cats      = "org.typelevel" %% "log4cats-core"                 % "2.6.0"
    lazy val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j"                % "2.6.0"
    lazy val effectTesting = "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test
  }

  object EmbeddedKafka {
    lazy val embeddedKafka = "io.github.embeddedkafka" %% "embedded-kafka" % "3.6.0" % Test
  }

  object Fs2 {
    lazy val core  = "co.fs2"          %% "fs2-core"  % "3.9.2"
    lazy val kafka = "com.github.fd4s" %% "fs2-kafka" % "3.2.0"
  }

  object Logback {
    lazy val classic = "ch.qos.logback" % "logback-classic" % "1.4.11" % Runtime
  }

  object ScalaTest {
    lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.17" % Test
  }

}
