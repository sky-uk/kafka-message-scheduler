package common

import akka.stream.ActorMaterializer

abstract class AkkaStreamBaseSpec extends AkkaBaseSpec {

  implicit val materializer = ActorMaterializer()

  override def afterAll(): Unit = {
    materializer.shutdown()
    super.afterAll()
  }
}
