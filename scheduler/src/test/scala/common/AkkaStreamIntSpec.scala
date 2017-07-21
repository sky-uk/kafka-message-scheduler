package common

import akka.stream.ActorMaterializer
import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterEach

abstract class AkkaStreamIntSpec extends TestKit(TestActorSystem())
  with BaseSpec with BeforeAndAfterEach {

  implicit val materializer = ActorMaterializer()

  override def afterEach(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterEach()
  }

}
