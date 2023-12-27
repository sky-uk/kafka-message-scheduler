package uk.sky.scheduler

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{Clock, IO}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{Assertion, LoneElement}
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.stubs.StubScheduleQueue.Status
import uk.sky.scheduler.stubs.StubScheduler
import uk.sky.scheduler.util.Generator.*
import uk.sky.scheduler.util.testSyntax.*

final class SchedulerSpec extends AsyncWordSpec, AsyncIOSpec, Matchers, LoneElement {

  "Scheduler" should {
    "add a scheduled event to the queue" in withRunningScheduler { scheduler =>
      for {
        time                 <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule             <- generateSchedule[IO](time)
        _                    <- scheduler.produce(schedule.update())
        resultPair           <- scheduler.runStreamAndTake(1).realTimeInstant
        (publishTime, result) = resultPair
      } yield {
        result.loneElement shouldBe schedule
        publishTime.toEpochMilli shouldBe time +- 50
      }
    }

    "schedule an event immediately if it has past" in withRunningScheduler { scheduler =>
      for {
        now                  <- Clock[IO].epochMilli
        schedule             <- generateSchedule[IO](now - 100_000L)
        _                    <- scheduler.produce(schedule.update())
        resultPair           <- scheduler.runStreamAndTake(1).realTimeInstant
        (publishTime, result) = resultPair
      } yield {
        result.loneElement shouldBe schedule
        publishTime.toEpochMilli shouldBe now +- 50
      }
    }

    "cancel a schedule on a delete message" in withRunningScheduler { scheduler =>
      for {
        _        <- scheduler.runStreamInBackground
        time     <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule <- generateSchedule[IO](time)
        _        <- scheduler.produce(schedule.update())
        _        <- scheduler.takeEvent.asserting(_ shouldBe (schedule.metadata.id -> Status.Scheduled))
        _        <- scheduler.produce(schedule.delete())
        result   <- scheduler.takeEvent
      } yield result shouldBe (schedule.metadata.id -> Status.Canceled)
    }

    "ignore a delete if it is an expiry" in withRunningScheduler { scheduler =>
      for {
        time       <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule   <- generateSchedule[IO](time)
        _          <- scheduler.produce(schedule.update())
        _          <- scheduler.produce(schedule.delete(expire = true))
        resultPair <- scheduler.runStreamAndTake(1).realTimeInstant
        (_, result) = resultPair
      } yield result.loneElement shouldBe schedule
    }
  }

  private def withRunningScheduler(
      test: StubScheduler[IO] => IO[Assertion]
  ): IO[Assertion] =
    StubScheduler[IO].use(test)

}
