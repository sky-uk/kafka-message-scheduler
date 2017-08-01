import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport.{Docker => docker}
import com.typesafe.sbt.packager.universal.UniversalPlugin.autoImport.Universal
import sbt.Keys._
import sbt.{Project, State, ThisBuild, taskKey}
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations.{runTest, setReleaseVersion => _, _}
import sbtrelease._

object Release {
  // Useful tasks to show what versions would be used if a release was performed.
  private val showReleaseVersion = taskKey[String]("the future version once releaseNextVersion has been applied to it")
  private val showNextVersion = taskKey[String]("the future version once releaseNextVersion has been applied to it")

  lazy val releaseSettings = Seq(
    releaseUseGlobalVersion := false,
    releaseVersionBump := sbtrelease.Version.Bump.Minor,
    releaseTagName := s"${name.value}-${version.value}",
    releaseTagComment := s"Releasing ${version.value} of module: ${name.value}",
    releasePublishArtifactsAction:= (publish in Universal).value,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      releaseStepCommand(ExtraReleaseCommands.initialVcsChecksCommand),
      inquireVersions,
      setReleaseVersion,
      runTest,
      tagRelease,
// Insert release release phases (JARs or Docker images as appropriate)
//      publishArtifacts,
//      ReleaseStep(releaseStepTask(publish in docker)),
      pushChanges
    ),
    showReleaseVersion := { val rV = releaseVersion.value.apply(version.value); println(rV); rV },
    showNextVersion := { val nV = releaseNextVersion.value.apply(version.value); println(nV); nV }
  )

  // Override the default implementation of sbtrelease.ReleaseStateTransformations.setReleaseVersion,
  // so it doesn't write to a version.sbt file.
  lazy val setReleaseVersion: ReleaseStep = setVersionOnly(_._1)

  def setVersionOnly(selectVersion: Versions => String): ReleaseStep = { st: State =>
    val vs = st.get(ReleaseKeys.versions).getOrElse(sys.error("No versions are set! Was this release part executed before inquireVersions?"))
    val selected = selectVersion(vs)

    st.log.info("Setting version to '%s'." format selected)
    val useGlobal = Project.extract(st).get(releaseUseGlobalVersion)

    reapply(Seq(
      if (useGlobal) version in ThisBuild := selected
      else version := selected
    ), st)
  }
}
