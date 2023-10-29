lazy val scala3 = "3.3.1"
lazy val scmUrl = "https://github.com/sky-uk/kafka-message-scheduler"

ThisBuild / scalaVersion := scala3
ThisBuild / organization := "uk.sky"
ThisBuild / licenses     := List("BSD New" -> url("https://opensource.org/licenses/BSD-3-Clause"))
ThisBuild / homepage     := Some(url(scmUrl))

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val scheduler = project
  .settings(CommonSettings.default)

lazy val root = Project("kafka-message-scheduler", file("."))
  .aggregate(scheduler)
