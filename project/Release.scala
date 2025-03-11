import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.Docker as docker
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal
import sbt.Keys.*
import sbt.taskKey
import sbtrelease.ReleasePlugin.autoImport.*
import sbtrelease.ReleaseStateTransformations.*
import sbtrelease.*

object Release {
  // Useful tasks to show what versions would be used if a release was performed.
  private val showReleaseVersion = taskKey[String]("the future version once releaseNextVersion has been applied to it")
  private val showNextVersion    = taskKey[String]("the future version once releaseNextVersion has been applied to it")

  lazy val releaseSettings = Seq(
    releaseUseGlobalVersion       := false,
    releaseVersionBump            := sbtrelease.Version.Bump.Minor,
    releaseTagName                := s"${name.value}-${version.value}",
    releaseTagComment             := s"Releasing ${version.value} of module: ${name.value}",
    releasePublishArtifactsAction := (Universal / publish).value,
    releaseProcess                := Seq[ReleaseStep](
      runClean,
      checkSnapshotDependencies,
      releaseStepCommand(ExtraReleaseCommands.initialVcsChecksCommand),
      inquireVersions,
      setReleaseVersion,
      runTest,
//      commitReleaseVersion,
//      tagRelease,
      ReleaseStep(releaseStepTask(docker / publish))
//      setNextVersion,
//      commitNextVersion,
//      pushChanges
    ),
    showReleaseVersion            := { val rV = releaseVersion.value.apply(version.value); println(rV); rV },
    showNextVersion               := { val nV = releaseNextVersion.value.apply(version.value); println(nV); nV }
  )
}
