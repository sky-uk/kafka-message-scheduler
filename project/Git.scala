import com.typesafe.sbt.GitPlugin.autoImport.{git, showCurrentGitBranch}
import sbtrelease.{Version, versionFormatError}

object Git {
  // Prefix our release tags with `kafka-message-scheduler`
  val VersionRegex = "kafka-message-scheduler-([0-9]+.[0-9]+.[0-9]+)-?(.*)?".r

  // Maven convention says that if you're working on `a.b.c-SNAPSHOT`, the NEXT RELEASE will be `a.b.c`.
  // The default behaviour of sbt-git says if the most recent release was `a.b.c`, you're working on `a.b.c-SNAPSHOT`.
  //
  // Since sbt-git is breaking convention, we work around by bumping the inferred version using our chosen version
  // bumping strategy (sbt-release's implementation saves us from having to write too much code).
  def bumpVersion(inputVersion: String): String = {
    Version.apply(inputVersion)
      .map(_.bump(Version.Bump.Minor).string)
      .getOrElse(versionFormatError)
  }

  lazy val gitSettings = Seq(
    showCurrentGitBranch,
    git.baseVersion := "0.0.0",
    git.useGitDescribe := true,
    git.gitTagToVersionNumber := {
      case VersionRegex(v, "") => Some(v)
      case VersionRegex(v, _) => Some(s"${bumpVersion(v)}-SNAPSHOT")
      case _ => None
    }
  )
}
