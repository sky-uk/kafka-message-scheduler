import Dependencies.*

enablePlugins(DockerComposePlugin)

libraryDependencies ++= Seq(
  Cats.core,
  Cats.effect,
  Cats.effectTesting,
  Circe.generic,
  Circe.parser,
  Fs2.core,
  Fs2.kafka,
  Logback.classic,
  ScalaTest.scalaTest
)
