package uk.sky.scheduler

import cats.effect.std.{MapRef, Queue}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testkit.TestControl
import cats.effect.{Deferred, IO, Outcome}
import monocle.syntax.all.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.exceptions.TestFailedException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{Assertion, EitherValues, OptionValues}
import uk.sky.scheduler.ScheduleQueue.CancelableSchedule
import uk.sky.scheduler.domain.{Metadata, Schedule, ScheduleEvent}
import uk.sky.scheduler.error.ScheduleError

import scala.concurrent.duration.*

final class ScheduleQueueSpec extends AsyncWordSpec, AsyncIOSpec, Matchers, OptionValues, EitherValues {

  "ScheduleQueue" when {

    "updating the Cancelable Fiber Ref" should {
      "add a Cancelable Fiber to the Ref when it is scheduled" in withContext { ctx =>
        import ctx.*

        for {
          _             <- scheduleQueue.schedule("key", scheduleEvent)
          maybeSchedule <- repository.get("key")
        } yield maybeSchedule shouldBe defined
      }

      "remove a Cancelable Fiber from the Ref when it is canceled" in withContext { ctx =>
        import ctx.*

        for {
          _             <- allowEnqueue.complete(())
          _             <- scheduleQueue.schedule("key", scheduleEvent)
          _             <- repository.get("key").asserting(_ shouldBe defined)
          _             <- scheduleQueue.cancel("key")
          maybeSchedule <- repository.get("key")
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
          _              = interval.toMillis shouldBe scheduleEvent.schedule.time
          _             <- control.tickFor(interval)
          maybeSchedule <- repository.get("key")
        } yield maybeSchedule shouldBe None
      }

      "cancel a schedule if it is updated" in withContext { ctx =>
        import ctx.*

        for {
          _          <- allowEnqueue.complete(())
          _          <- scheduleQueue.schedule("key", scheduleEvent)
          schedule   <- repository.get("key").map(_.value)
          newSchedule = scheduleEvent.focus(_.schedule.time).modify(_ + +100_000L)
          _          <- scheduleQueue.schedule("key", newSchedule)
          outcome    <- schedule.join.testTimeout()
        } yield outcome shouldBe Outcome.canceled[IO, Throwable, Unit]
      }

      "error if the scheduled time is not valid" in withContext { ctx =>
        import ctx.*

        val invalidSchedule = scheduleEvent.focus(_.schedule.time).replace(Long.MaxValue)

        for {
          result <- scheduleQueue.schedule("key", invalidSchedule)
        } yield result.left.value shouldBe ScheduleError.InvalidTimeError("key", invalidSchedule.schedule.time)
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
          interval.toMillis shouldBe scheduleEvent.schedule.time
          result.value shouldBe scheduleEvent
        }
      }

      "offer a ScheduleEvent to the Queue immediately if the specified time has passed" in withContext { ctx =>
        import ctx.*

        for {
          _           <- allowEnqueue.complete(())
          now         <- IO.realTimeInstant
          pastSchedule = scheduleEvent.focus(_.schedule.time).replace(now.minusSeconds(100_000).toEpochMilli)
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
          interval.toMillis shouldBe scheduleEvent.schedule.time
          result.value shouldBe scheduleEvent
        }
      }
    }
  }

  extension [A](fa: IO[A]) {
    def testTimeout(timeout: FiniteDuration = 10.seconds): IO[A] =
      fa.timeoutTo(timeout, IO.raiseError(TestFailedException(s"Operation did not complete within $timeout", 0)))
  }

  given Arbitrary[Metadata]                = Arbitrary(Gen.resultOf(Metadata.apply))
  given Arbitrary[Schedule]                = Arbitrary(Gen.resultOf(Schedule.apply))
  val scheduleEventArb: Gen[ScheduleEvent] = Gen.resultOf(ScheduleEvent.apply)

  private trait TestContext {
    val repository: Repository[IO, String, CancelableSchedule[IO]]
    val allowEnqueue: Deferred[IO, Unit]
    val eventQueue: Queue[IO, ScheduleEvent]
    val scheduleQueue: ScheduleQueue[IO]
    val scheduleEvent: ScheduleEvent
  }

  private def withContext(test: TestContext => IO[Assertion]): IO[Assertion] =
    for {
      now        <- IO.realTimeInstant
      repo       <- MapRef.ofScalaConcurrentTrieMap[IO, String, CancelableSchedule[IO]].map(Repository.apply)
      deferred   <- Deferred[IO, Unit]
      queue      <- Queue.unbounded[IO, ScheduleEvent]
      schedule   <- IO.fromOption(scheduleEventArb.sample)(TestFailedException("Could not generate a schedule", 0))
                      .map(_.focus(_.schedule.time).replace(now.plusSeconds(10).toEpochMilli))
      testContext = new TestContext {
                      override val repository: Repository[IO, String, CancelableSchedule[IO]] = repo
                      override val allowEnqueue: Deferred[IO, Unit]                           = deferred
                      override val eventQueue: Queue[IO, ScheduleEvent]                       = queue
                      override val scheduleQueue: ScheduleQueue[IO]                           = ScheduleQueue(repo, deferred, queue)
                      override val scheduleEvent: ScheduleEvent                               = schedule
                    }
      assertion  <- test(testContext)
    } yield assertion

}
