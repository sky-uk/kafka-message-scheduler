package uk.sky.scheduler

import cats.effect.IO
import cats.effect.Deferred
import cats.effect.std.{MapRef, Queue}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testkit.TestControl
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import uk.sky.scheduler.ScheduleQueue.CancelableSchedule
import uk.sky.scheduler.domain.ScheduleEvent

final class ScheduleQueueSpec extends AsyncWordSpec, AsyncIOSpec, Matchers {

  "ScheduleQueue" when {

    "updating the Cancelable Fiber Ref" should {
      "add a Cancelable Fiber to the Ref when it is scheduled" in withContext { ctx =>
        import ctx.*

        for {
          scheduleEvent <-
            IO.realTimeInstant.map(instant => scheduleEvent.copy(time = instant.plusSeconds(10).toEpochMilli))
          control       <- TestControl.execute(scheduleQueue.schedule("key", scheduleEvent))
          _             <- control.results.asserting(foo => foo shouldBe None)
          _             <- control.tick
          _             <- control.results.asserting(bar => bar shouldBe None)
        } yield true shouldBe true
      }

      "remove a Cancelable Fiber from the Ref when it is offered to the Queue" in {
        pending
      }
    }

    "offering to the Queue" should {
      "offer a ScheduleEvent to the Queue at the specified time" in {
        pending
      }

      "offer a ScheduleEvent to the Queue immediately if the specified time has passed" in {
        pending
      }
    }

    "deferred offering to the Queue" should {
      "not offer a due Schedule to the Queue until it is allowed" in {
        pending
      }
    }
  }

  private trait TestContext {
    val scheduleRef: MapRef[IO, String, Option[CancelableSchedule[IO]]]
    val allowEnqueue: Deferred[IO, Unit]
    val eventQueue: Queue[IO, ScheduleEvent]
    val scheduleQueue: ScheduleQueue[IO]
    val scheduleEvent: ScheduleEvent
  }

  private def withContext(test: TestContext => IO[Assertion]): IO[Assertion] = {
    val scheduleEventArb: Gen[ScheduleEvent] = Gen.resultOf(ScheduleEvent.apply)
    for {
      ref        <- MapRef.ofScalaConcurrentTrieMap[IO, String, CancelableSchedule[IO]]
      deferred   <- Deferred[IO, Unit]
      queue      <- Queue.unbounded[IO, ScheduleEvent]
      testContext = new TestContext {
                      override val scheduleRef: MapRef[IO, String, Option[CancelableSchedule[IO]]] = ref
                      override val allowEnqueue: Deferred[IO, Unit]                                = deferred
                      override val eventQueue: Queue[IO, ScheduleEvent]                            = queue
                      override val scheduleQueue: ScheduleQueue[IO]                                = ScheduleQueue.apply(ref, deferred, queue)
                      override val scheduleEvent: ScheduleEvent                                    = scheduleEventArb.sample.get
                    }
      assertion  <- test(testContext)
    } yield assertion
  }

}
