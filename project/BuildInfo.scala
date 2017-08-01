import sbt.Keys._
import sbtbuildinfo.BuildInfoPlugin.autoImport._

object BuildInfo {
  lazy val buildInfoSettings = Seq(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.sky"
  )
}
