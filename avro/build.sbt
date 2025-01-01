import Dependencies.*

enablePlugins(BuildInfoPlugin)

run / fork := true

libraryDependencies ++= Seq(
  Decline.core,
  Decline.effect,
  Fs2.io
)
