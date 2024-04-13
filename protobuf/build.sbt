import sbt.*
import sbt.Keys.*
import Dependencies.*

enablePlugins(Fs2Grpc)

libraryDependencies ++= Seq(
  Cats.core,
  Cats.effect,
  Netty.grpc,
  ScalaPb.runtime
)
