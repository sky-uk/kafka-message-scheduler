package uk.sky.scheduler

import cats.effect.std.{MapRef, Queue}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testkit.TestControl
import cats.effect.{Deferred, IO, Outcome}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{Assertion, OptionValues}
import uk.sky.scheduler.ScheduleQueue.CancelableSchedule
import uk.sky.scheduler.domain.ScheduleEvent

final class ScheduleQueueSpec extends AsyncWordSpec, AsyncIOSpec, Matchers, OptionValues {

  "ScheduleQueue" when {

    "updating the Cancelable Fiber Ref" should {
      "add a Cancelable Fiber to the Ref when it is scheduled" in withContext { ctx =>
        import ctx.*

        for {
          _             <- scheduleQueue.schedule("key", scheduleEvent)
          maybeSchedule <- scheduleRef("key").get
        } yield maybeSchedule shouldBe defined
      }

      "remove a Cancelable Fiber from the Ref when it is canceled" in withContext { ctx =>
        import ctx.*

        for {
          _             <- allowEnqueue.complete(())
          _             <- scheduleQueue.schedule("key", scheduleEvent)
          _             <- scheduleRef("key").get.asserting(_ shouldBe defined)
          _             <- scheduleQueue.cancel("key")
          maybeSchedule <- scheduleRef("key").get
        } yield maybeSchedule shouldBe None
      }

      "remove a Cancelable Fiber from the Ref when it is due" in withContext { ctx =>
        import ctx.*

        for {
          _             <- allowEnqueue.complete(())
          control       <- TestControl.execute(scheduleQueue.schedule("key", scheduleEvent))
          _             <- control.results.asserting(_ shouldBe None)
          _             <- control.tick
          interval      <- control.nextInterval
          _              = interval.toMillis shouldBe scheduleEvent.time
          _             <- control.tickFor(interval)
          maybeSchedule <- scheduleRef("key").get
        } yield maybeSchedule shouldBe None
      }
    }

    "offering to the Queue" should {
      "offer a ScheduleEvent to the Queue at the specified time" in withContext { ctx =>
        import ctx.*

        for {
          _        <- allowEnqueue.complete(())
          control  <- TestControl.execute(scheduleQueue.schedule("key", scheduleEvent))
          _        <- control.results.asserting(_ shouldBe None)
          _        <- control.tick
          interval <- control.nextInterval
          _        <- control.tickFor(interval)
          result   <- eventQueue.tryTake
        } yield {
          interval.toMillis shouldBe scheduleEvent.time
          result.value shouldBe scheduleEvent
        }
      }

      "offer a ScheduleEvent to the Queue immediately if the specified time has passed" in withContext { ctx =>
        import ctx.*

        for {
          _           <- allowEnqueue.complete(())
          now         <- IO.realTimeInstant
          pastSchedule = scheduleEvent.copy(time = now.minusSeconds(100_000).toEpochMilli)
          control     <- TestControl.execute(scheduleQueue.schedule("key", pastSchedule))
          _           <- control.results.asserting(_ shouldBe None)
          _           <- control.tick
          interval    <- control.nextInterval
          _           <- control.tickAll
          result      <- eventQueue.tryTake
        } yield {
          interval.toMillis should be < now.toEpochMilli
          result.value shouldBe pastSchedule
        }
      }
    }

    "deferred offering to the Queue" should {
      "not offer a due Schedule to the Queue until it is allowed" in withContext { ctx =>
        import ctx.*

        for {
          control  <- TestControl.execute(scheduleQueue.schedule("key", scheduleEvent))
          _        <- control.results.asserting(_ shouldBe None)
          _        <- control.tick
          interval <- control.nextInterval
          _        <- control.tickFor(interval)
          _        <- eventQueue.tryTake.asserting(_ shouldBe None)
          _        <- allowEnqueue.complete(())
          _        <- control.tickAll
          result   <- eventQueue.tryTake
        } yield {
          interval.toMillis shouldBe scheduleEvent.time
          result.value shouldBe scheduleEvent
        }
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
      now        <- IO.realTimeInstant
      ref        <- MapRef.ofScalaConcurrentTrieMap[IO, String, CancelableSchedule[IO]]
      deferred   <- Deferred[IO, Unit]
      queue      <- Queue.unbounded[IO, ScheduleEvent]
      testContext = new TestContext {
                      override val scheduleRef: MapRef[IO, String, Option[CancelableSchedule[IO]]] = ref
                      override val allowEnqueue: Deferred[IO, Unit]                                = deferred
                      override val eventQueue: Queue[IO, ScheduleEvent]                            = queue
                      override val scheduleQueue: ScheduleQueue[IO]                                = ScheduleQueue.apply(ref, deferred, queue)
                      override val scheduleEvent: ScheduleEvent                                    =
                        scheduleEventArb.sample.get.copy(time = now.plusSeconds(10).toEpochMilli)
                    }
      assertion  <- test(testContext)
    } yield assertion
  }

}
