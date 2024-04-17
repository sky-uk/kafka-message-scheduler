import sbt.*
import sbt.Keys.*
import Dependencies.*

enablePlugins(Fs2Grpc)

libraryDependencies ++= Seq(
  Cats.core,
  Cats.effect,
  Cats.log4cats,
  Chimney.chimney,
  Otel4s.java,
  Grpc.netty,
  Grpc.services,
  ScalaPb.runtime
)
