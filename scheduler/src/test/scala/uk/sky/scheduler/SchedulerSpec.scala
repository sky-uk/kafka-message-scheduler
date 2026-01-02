package uk.sky.scheduler

import cats.effect.{Clock, IO}
import org.scalatest.{Assertion, LoneElement}
import org.typelevel.otel4s.metrics.Meter
import uk.sky.scheduler.domain.ScheduleEvent
import uk.sky.scheduler.stubs.StubScheduler
import uk.sky.scheduler.syntax.all.*
import uk.sky.scheduler.util.Generator.*
import uk.sky.scheduler.util.testSyntax.*

final class SchedulerSpec extends AsyncSpecBase, LoneElement {

  "Scheduler" should {
    "add a scheduled event to the queue" in withRunningScheduler { scheduler =>
      for {
        time                    <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule                <- generateSchedule[IO](time)
        _                       <- scheduler.produceAndAssertScheduled(schedule)
        resultPair              <- scheduler.takeEvent.realTimeInstant
        (publishTime, testEvent) = resultPair
      } yield {
        testEvent.expired.event shouldBe schedule
        publishTime.toEpochMilli shouldBe time +- 100
      }
    }

    "schedule an event immediately if it has past" in withRunningScheduler { scheduler =>
      for {
        schedule                <- generateSchedule[IO](_.minusSeconds(100))
        _                       <- scheduler.produceAndAssertScheduled(schedule)
        now                     <- Clock[IO].epochMilli
        resultPair              <- scheduler.takeEvent.realTimeInstant
        (publishTime, testEvent) = resultPair
      } yield {
        testEvent.expired.event shouldBe schedule
        publishTime.toEpochMilli shouldBe now +- 50
      }
    }

    "cancel a schedule on a delete message" in withRunningScheduler { scheduler =>
      for {
        schedule <- generateSchedule[IO](_.plusSeconds(5))
        _        <- scheduler.produceAndAssertScheduled(schedule)
        _        <- scheduler.produce(schedule.delete())
        result   <- scheduler.takeEvent
      } yield result.canceled.id shouldBe schedule.metadata.id
    }

    "ignore a delete if it is an expiry" in withRunningScheduler { scheduler =>
      for {
        schedule <- generateSchedule[IO](_.plusSeconds(5))
        _        <- scheduler.produceAndAssertScheduled(schedule)
        _        <- scheduler.produce(schedule.delete(expire = true))
        result   <- scheduler.takeEvent
      } yield result.expired.event shouldBe schedule
    }
  }

  extension (scheduler: StubScheduler[IO]) {
    def produceAndAssertScheduled(
        event: ScheduleEvent
    ): IO[Assertion] =
      for {
        _         <- scheduler.produce(event.update())
        testEvent <- scheduler.takeEvent
      } yield testEvent.scheduled.event shouldBe event
  }

  private def withRunningScheduler(
      test: StubScheduler[IO] => IO[Assertion]
  )(using Meter[IO]): IO[Assertion] =
    StubScheduler[IO].use { stubScheduler =>
      stubScheduler.runStreamInBackground.surround(test(stubScheduler))
    }

}
