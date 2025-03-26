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

  val kafkaPort = Map("KAFKA_PORT" -> freePort.toString)

  val allRegistries = sys.env.get("CONTAINER_REPOSITORIES").fold(List.empty[String])(_.split(" ").toList)
  val repository    = allRegistries.headOption.fold("test")(r => r)

  val dockerRepository =
    Map("DOCKER_REPOSITORY" -> repository)

  lazy val settings = Seq(
    variablesForSubstitution ++= kafkaPort ++ dockerRepository
  )

}
