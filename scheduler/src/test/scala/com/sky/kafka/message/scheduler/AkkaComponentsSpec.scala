package com.sky.kafka.message.scheduler

import akka.actor.Props
import akka.actor.SupervisorStrategy.Restart
import akka.testkit.TestActorRef
import common.{AkkaBaseSpec, AkkaStreamBaseSpec}

class AkkaComponentsSpec extends AkkaBaseSpec {

  "decider" should {
    "return a 'Restart' supervision strategy" in {
      val supervisor = TestActorRef[SchedulingActor](Props(new SchedulingActor(null, null)))
      val strategy = supervisor.underlyingActor.supervisorStrategy.decider

      strategy(new Exception("Any exception")) should be (Restart)
    }
  }
}
