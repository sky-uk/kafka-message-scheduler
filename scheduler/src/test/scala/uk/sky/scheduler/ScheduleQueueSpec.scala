package uk.sky.scheduler

import cats.effect.std.Queue
import cats.effect.{Deferred, IO, Ref}
import monocle.syntax.all.*
import org.scalatest.{Assertion, EitherValues, OptionValues}
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.util.Generator.*
import uk.sky.scheduler.util.testSyntax.*

import scala.concurrent.duration.*

final class ScheduleQueueSpec extends AsyncSpecBase, OptionValues, EitherValues {

  "ScheduleQueue" when {

    "updating the Repository" should {
      "add a ScheduleEvent to the Repository when it is scheduled" in withContext {
        case TestContext(repository, _, _, _, scheduleQueue, scheduleEvent) =>
          for {
            _             <- scheduleQueue.schedule("key", scheduleEvent)
            maybeSchedule <- repository.get("key")
          } yield maybeSchedule shouldBe Some(scheduleEvent)
      }

      "remove a ScheduleEvent from the Repository when it is canceled" in withContext {
        case TestContext(repository, _, _, _, scheduleQueue, scheduleEvent) =>
          for {
            _             <- scheduleQueue.schedule("key", scheduleEvent)
            _             <- repository.get("key").asserting(_ shouldBe Some(scheduleEvent))
            _             <- scheduleQueue.cancel("key")
            maybeSchedule <- repository.get("key")
          } yield maybeSchedule shouldBe None
      }

      "update a ScheduleEvent in the Repository when rescheduled" in withContext {
        case TestContext(repository, _, _, _, scheduleQueue, scheduleEvent) =>
          for {
            _             <- scheduleQueue.schedule("key", scheduleEvent)
            _             <- repository.get("key").asserting(_ shouldBe Some(scheduleEvent))
            newSchedule    = scheduleEvent.focus(_.schedule.time).modify(_ + 100_000L)
            _             <- scheduleQueue.schedule("key", newSchedule)
            maybeSchedule <- repository.get("key")
          } yield maybeSchedule shouldBe Some(newSchedule)
      }
    }

    "scheduler fiber" should {
      "remove a ScheduleEvent from the Repository when it is due" in withSchedulerFiber {
        case TestContext(repository, allowEnqueue, _, outputQueue, scheduleQueue, scheduleEvent) =>
          for {
            _             <- allowEnqueue.complete(())
            _             <- scheduleQueue.schedule("key", scheduleEvent)
            _             <- IO.sleep(200.millis) // Wait for schedule to fire (100ms delay + buffer)
            maybeSchedule <- repository.get("key")
            result        <- outputQueue.tryTake
          } yield {
            maybeSchedule shouldBe None
            result shouldBe Some(scheduleEvent)
          }
      }

      "offer a ScheduleEvent to the Queue at the specified time" in withSchedulerFiber {
        case TestContext(_, allowEnqueue, _, outputQueue, scheduleQueue, scheduleEvent) =>
          for {
            _      <- allowEnqueue.complete(())
            _      <- scheduleQueue.schedule("key", scheduleEvent)
            _      <- IO.sleep(200.millis) // Wait for schedule to fire (100ms delay + buffer)
            result <- outputQueue.tryTake
          } yield result shouldBe Some(scheduleEvent)
      }

      "offer a ScheduleEvent to the Queue immediately if the specified time has passed" in withSchedulerFiber {
        case TestContext(repository, allowEnqueue, _, outputQueue, scheduleQueue, scheduleEvent) =>
          for {
            _           <- allowEnqueue.complete(())
            now         <- IO.realTimeInstant
            pastSchedule = scheduleEvent.focus(_.schedule.time).replace(now.minusSeconds(100).toEpochMilli)
            _           <- scheduleQueue.schedule("key", pastSchedule)
            _           <- IO.sleep(50.millis) // Small delay for processing
            stored      <- repository.get("key")
            result      <- outputQueue.tryTake
          } yield {
            stored shouldBe None
            result shouldBe Some(pastSchedule)
          }
      }

      "not offer a due Schedule to the Queue until it is allowed" in withSchedulerFiber {
        case TestContext(_, allowEnqueue, _, outputQueue, scheduleQueue, scheduleEvent) =>
          for {
            _      <- scheduleQueue.schedule("key", scheduleEvent)
            _      <- IO.sleep(200.millis)                           // Wait past the schedule time
            _      <- outputQueue.tryTake.asserting(_ shouldBe None) // Should not fire yet
            _      <- allowEnqueue.complete(())
            _      <- IO.sleep(50.millis)                            // Small delay for processing
            result <- outputQueue.tryTake
          } yield result shouldBe Some(scheduleEvent)
      }

      "verify schedule hasn't changed before firing" in withSchedulerFiber {
        case TestContext(repository, allowEnqueue, _, outputQueue, scheduleQueue, scheduleEvent) =>
          for {
            _          <- allowEnqueue.complete(())
            _          <- scheduleQueue.schedule("key", scheduleEvent)
            newSchedule = scheduleEvent.focus(_.schedule.time).modify(_ + 200L) // Add 200ms
            _          <- IO.sleep(50.millis)                                   // Wait a bit
            _          <- scheduleQueue.schedule("key", newSchedule)            // Update before first fires
            _          <- IO.sleep(100.millis)                                  // Wait for original time
            result1    <- outputQueue.tryTake
            _          <- IO.sleep(200.millis)                                  // Wait for new schedule time
            result2    <- outputQueue.tryTake
            stored     <- repository.get("key")
          } yield {
            result1 shouldBe None              // Original should not fire
            result2 shouldBe Some(newSchedule) // New schedule should fire
            stored shouldBe None               // Should be removed after firing
          }
      }

      "handle very large delays without overflow" in withSchedulerFiber {
        case TestContext(repository, allowEnqueue, _, outputQueue, scheduleQueue, scheduleEvent) =>
          for {
            _             <- allowEnqueue.complete(())
            farFuture      = Long.MaxValue - 1000L // Very far in the future
            futureSchedule = scheduleEvent.focus(_.schedule.time).replace(farFuture)
            _             <- scheduleQueue.schedule("key", futureSchedule)
            _             <- IO.sleep(100.millis)  // Give it time to process
            stored        <- repository.get("key")
            result        <- outputQueue.tryTake
          } yield {
            stored shouldBe Some(futureSchedule) // Should still be in repository
            result shouldBe None                 // Should not have fired
          }
      }
    }
  }

  final case class TestContext(
      repository: Repository[IO, String, ScheduleEvent],
      allowEnqueue: Deferred[IO, Unit],
      priorityQueue: PriorityScheduleQueue[IO],
      outputQueue: Queue[IO, ScheduleEvent],
      scheduleQueue: ScheduleQueue[IO],
      scheduleEvent: ScheduleEvent
  )

  private def withContext(test: TestContext => IO[Assertion])(using Meter[IO]): IO[Assertion] =
    for {
      repository    <- Repository.ofScalaConcurrentTrieMap[IO, String, ScheduleEvent]("test")
      allowEnqueue  <- Deferred[IO, Unit]
      priorityQueue <- PriorityScheduleQueue[IO]
      outputQueue   <- Queue.unbounded[IO, ScheduleEvent]
      initialWakeup <- Deferred[IO, Unit]
      wakeupRef     <- Ref.of[IO, Deferred[IO, Unit]](initialWakeup)
      scheduleEvent <- generateSchedule[IO](_.plusSeconds(10))
      testContext    = TestContext(
                         repository = repository,
                         allowEnqueue = allowEnqueue,
                         priorityQueue = priorityQueue,
                         outputQueue = outputQueue,
                         scheduleQueue = ScheduleQueue(allowEnqueue, repository, priorityQueue, outputQueue, wakeupRef),
                         scheduleEvent = scheduleEvent
                       )
      assertion     <- test(testContext)
    } yield assertion

  private def withSchedulerFiber(test: TestContext => IO[Assertion])(using Meter[IO]): IO[Assertion] =
    for {
      repository    <- Repository.ofScalaConcurrentTrieMap[IO, String, ScheduleEvent]("test")
      allowEnqueue  <- Deferred[IO, Unit]
      priorityQueue <- PriorityScheduleQueue[IO]
      outputQueue   <- Queue.unbounded[IO, ScheduleEvent]
      initialWakeup <- Deferred[IO, Unit]
      wakeupRef     <- Ref.of[IO, Deferred[IO, Unit]](initialWakeup)
      scheduleEvent <- generateSchedule[IO](_.plusMillis(100)) // Short delay for fast tests
      testContext    = TestContext(
                         repository = repository,
                         allowEnqueue = allowEnqueue,
                         priorityQueue = priorityQueue,
                         outputQueue = outputQueue,
                         scheduleQueue = ScheduleQueue(allowEnqueue, repository, priorityQueue, outputQueue, wakeupRef),
                         scheduleEvent = scheduleEvent
                       )
      assertion     <- ScheduleQueue
                         .schedulerFiber(allowEnqueue, repository, priorityQueue, outputQueue, wakeupRef)
                         .background
                         .surround(test(testContext))
    } yield assertion

}
