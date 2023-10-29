import Dependencies.*

libraryDependencies ++= Seq(
  Cats.core,
  Cats.effect,
  Cats.log4cats,
  Cats.log4catsSlf4j,
  Fs2.core,
  Fs2.kafka,
  Logback.classic,
  ScalaTest.scalaTest
)
