package com.sky.kms.unit

import akka.Done
import akka.actor.PoisonPill
import akka.testkit.{TestActorRef, TestProbe}
import cats.Eval
import com.sky.kms.actors.TerminatorActor
import com.sky.kms.base.AkkaSpecBase
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future

class TerminatorActorSpec extends AkkaSpecBase with MockitoSugar {

  "A terminator actor" must {
    "trigger terminate and stop when any of the actors it is monitoring dies" in {
      val terminate = mock[Eval[Future[Done]]]
      val probe = TestProbe()

      val terminator = TestActorRef(new TerminatorActor(terminate, probe.ref))
      watch(terminator)
      probe.ref ! PoisonPill

      verify(terminate).value
      expectTerminated(terminator)
    }
  }

}
