package base

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.{DefaultDockerClientConfig, DockerClientImpl}
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient

import scala.compat.java8.DurationConverters._
import scala.concurrent.duration._

trait DockerBase {

  val dockerConfig: DefaultDockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder.build
  val httpClient: ApacheDockerHttpClient      = new ApacheDockerHttpClient.Builder()
    .dockerHost(dockerConfig.getDockerHost)
    .sslConfig(dockerConfig.getSSLConfig)
    .maxConnections(100)
    .connectionTimeout(30.seconds.toJava)
    .responseTimeout(45.seconds.toJava)
    .build()

  val dockerClient: DockerClient = DockerClientImpl.getInstance(dockerConfig, httpClient)

}
