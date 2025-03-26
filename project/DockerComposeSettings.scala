import com.tapad.docker.DockerComposePlugin.autoImport.variablesForSubstitution

import java.net.ServerSocket

object DockerComposeSettings {

  val freePort: Int = {
    val socket = new ServerSocket(0)
    socket.setReuseAddress(true)
    val port   = socket.getLocalPort
    socket.close()
    port
  }

  val kafkaPort        = Map("KAFKA_PORT" -> freePort.toString)
  val dockerRepository = sys.env.get("DOCKER_REPOSITORY").fold(Map("DOCKER_REPOSITORY" -> "test"))(repo => Map("DOCKER_REPOSITORY" -> repo))

  lazy val settings = Seq(
    variablesForSubstitution ++= kafkaPort ++ dockerRepository
  )

}
