package uk.sky.scheduler

import cats.effect.std.{MapRef, Queue, Supervisor}
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.testkit.TestControl
import cats.effect.{Deferred, IO, Outcome}
import monocle.syntax.all.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{Assertion, EitherValues, OptionValues}
import uk.sky.scheduler.ScheduleQueue.CancelableSchedule
import uk.sky.scheduler.domain.{Schedule, ScheduleEvent}
import uk.sky.scheduler.util.Generator.*
import uk.sky.scheduler.util.testSyntax.*

import scala.concurrent.duration.*

final class ScheduleQueueSpec extends AsyncWordSpec, AsyncIOSpec, Matchers, OptionValues, EitherValues {

  "ScheduleQueue" when {

    "updating the CancelableSchedule" should {
      "add a CancelableSchedule to the Repository when it is scheduled" in withContext {
        case TestContext(repository, _, _, scheduleQueue, scheduleEvent) =>
          for {
            _             <- scheduleQueue.schedule("key", scheduleEvent)
            maybeSchedule <- repository.get("key")
          } yield maybeSchedule shouldBe defined
      }

      "remove a CancelableSchedule from the Repository when it is canceled" in withContext {
        case TestContext(repository, allowEnqueue, _, scheduleQueue, scheduleEvent) =>
          for {
            _             <- allowEnqueue.complete(())
            _             <- scheduleQueue.schedule("key", scheduleEvent)
            _             <- repository.get("key").asserting(_ shouldBe defined)
            _             <- scheduleQueue.cancel("key")
            maybeSchedule <- repository.get("key")
          } yield maybeSchedule shouldBe None
      }

      "remove a CancelableSchedule from the Repository when it is due" in withContext {
        case TestContext(repository, allowEnqueue, _, scheduleQueue, scheduleEvent) =>
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

      "cancel a schedule if it is updated" in withContext {
        case TestContext(repository, allowEnqueue, _, scheduleQueue, scheduleEvent) =>
          for {
            _          <- allowEnqueue.complete(())
            _          <- scheduleQueue.schedule("key", scheduleEvent)
            schedule   <- repository.get("key").map(_.value)
            newSchedule = scheduleEvent.focus(_.schedule.time).modify(_ + 100_000L)
            _          <- scheduleQueue.schedule("key", newSchedule)
            outcome    <- schedule.join.testTimeout()
          } yield outcome shouldBe Outcome.canceled[IO, Throwable, Unit]
      }

      "schedule for the maximum finite duration if the scheduled time is too large" in withContext {
        case TestContext(_, allowEnqueue, _, scheduleQueue, scheduleEvent) =>
          val invalidSchedule = scheduleEvent.focus(_.schedule.time).replace(Long.MaxValue)

          for {
            _        <- allowEnqueue.complete(())
            control  <- TestControl.execute(scheduleQueue.schedule("key", invalidSchedule))
            _        <- control.results.asserting(_ shouldBe None)
            _        <- control.tick
            interval <- control.nextInterval
            _        <- control.tickFor(interval)
          } yield interval.toNanos shouldBe Long.MaxValue
      }
    }

    "offering to the Queue" should {
      "offer a ScheduleEvent to the Queue at the specified time" in withContext {
        case TestContext(_, allowEnqueue, eventQueue, scheduleQueue, scheduleEvent) =>
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

      "offer a ScheduleEvent to the Queue immediately if the specified time has passed" in withContext {
        case TestContext(repository, allowEnqueue, eventQueue, scheduleQueue, scheduleEvent) =>
          for {
            _           <- allowEnqueue.complete(())
            now         <- IO.realTimeInstant
            pastSchedule = scheduleEvent.focus(_.schedule.time).replace(now.minusSeconds(100_000).toEpochMilli)
            control     <- TestControl.execute(scheduleQueue.schedule("key", pastSchedule))
            _           <- control.results.asserting(_ shouldBe None)
            _           <- control.tick
            interval    <- control.nextInterval
            _           <- control.tickAll
            stored      <- repository.get("key")
            result      <- eventQueue.tryTake
          } yield {
            interval.toMillis should be < now.toEpochMilli
            stored shouldBe empty
            result.value shouldBe pastSchedule
          }
      }
    }

    "deferred offering to the Queue" should {
      "not offer a due Schedule to the Queue until it is allowed" in withContext {
        case TestContext(_, allowEnqueue, eventQueue, scheduleQueue, scheduleEvent) =>
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

  final case class TestContext(
      repository: Repository[IO, String, CancelableSchedule[IO]],
      allowEnqueue: Deferred[IO, Unit],
      eventQueue: Queue[IO, ScheduleEvent],
      scheduleQueue: ScheduleQueue[IO],
      scheduleEvent: ScheduleEvent
  )

  private def withContext(test: TestContext => IO[Assertion]): IO[Assertion] =
    Supervisor[IO].use { supervisor =>
      for {
        repository    <- MapRef.ofScalaConcurrentTrieMap[IO, String, CancelableSchedule[IO]].map(Repository.apply)
        allowEnqueue  <- Deferred[IO, Unit]
        eventQueue    <- Queue.unbounded[IO, ScheduleEvent]
        scheduleEvent <- generateSchedule[IO](_.plusSeconds(10))
        testContext    = TestContext(
                           repository = repository,
                           allowEnqueue = allowEnqueue,
                           eventQueue = eventQueue,
                           scheduleQueue = ScheduleQueue(allowEnqueue, repository, eventQueue, supervisor),
                           scheduleEvent = scheduleEvent
                         )
        assertion     <- test(testContext)
      } yield assertion
    }

}
