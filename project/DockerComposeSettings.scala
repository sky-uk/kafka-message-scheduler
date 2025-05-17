import DockerPublish.registry
import com.tapad.docker.DockerComposePlugin.autoImport.variablesForSubstitution
import sbt.Def

import java.net.ServerSocket
import scala.util.Using

object DockerComposeSettings {

  def freePort: Int =
    Using.resource(new ServerSocket(0)) { socket =>
      socket.setReuseAddress(true)
      socket.getLocalPort
    }

  lazy val kafkaPort: (String, String) = "KAFKA_PORT" -> freePort.toString

  lazy val settings: Seq[Def.Setting[?]] = Seq(
    variablesForSubstitution ++= Map(
      "CONTAINER_REPOSITORY" -> registry,
      kafkaPort
    )
  )

}
