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

}
