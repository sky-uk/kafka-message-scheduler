import com.tapad.docker.DockerComposePlugin.autoImport.variablesForSubstitution
import DockerPublish.allRegistries

import java.net.ServerSocket

object DockerComposeSettings {

  val freePort: Int = {
    val socket = new ServerSocket(0)
    socket.setReuseAddress(true)
    val port   = socket.getLocalPort
    socket.close()
    port
  }

  val kafkaPort = Map("KAFKA_PORT" -> freePort.toString)

  val repository = allRegistries.head

  val containerRepository =
    Map("CONTAINER_REPOSITORY" -> repository)

  lazy val settings = Seq(
    variablesForSubstitution ++= kafkaPort ++ containerRepository
  )

}
