package uk.sky.scheduler

import cats.effect.IO
import cats.effect.testing.scalatest.AsyncIOSpec
import cats.syntax.all.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{Assertion, LoneElement}
import uk.sky.scheduler.Message.Headers
import uk.sky.scheduler.domain.{Metadata, Schedule, ScheduleEvent}
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.stubs.StubScheduler
import uk.sky.scheduler.util.testSyntax.*

final class SchedulerSpec extends AsyncWordSpec, AsyncIOSpec, Matchers, LoneElement {

  "Scheduler" should {
    "add a scheduled event to the queue" in withRunningScheduler { scheduler =>
      for {
        _                    <- scheduler.signalLoaded
        time                 <- IO.realTimeInstant.map(_.plusSeconds(5).toEpochMilli)
        schedule              = scheduleEvent("id", time = time)
        _                    <- scheduler.produce(messages(schedule)*)
        resultPair           <- scheduler.consume(1).realTimeInstant
        (publishTime, result) = resultPair
      } yield {
        result.loneElement shouldBe schedule
        publishTime.toEpochMilli shouldBe time +- 50
      }
    }
  }

  private def scheduleEvent(
      id: String,
      value: Option[String] = None,
      time: Long,
      headers: Map[String, String] = Map.empty
  ): ScheduleEvent =
    ScheduleEvent(
      metadata = Metadata(
        id = id,
        scheduleTopic = "input-topic"
      ),
      schedule = Schedule(
        time = time,
        topic = "output-topic",
        key = id.getBytes,
        value = value.map(_.getBytes),
        headers = headers.view.mapValues(_.getBytes).toMap
      )
    )

  private def messages(
      scheduleEvents: ScheduleEvent*
  ): List[Message[Either[ScheduleError, Option[ScheduleEvent]]]] =
    scheduleEvents.toList.map { scheduleEvent =>
      Message(
        key = scheduleEvent.metadata.id,
        source = scheduleEvent.metadata.scheduleTopic,
        value = scheduleEvent.some.asRight[ScheduleError],
        headers = Headers.empty
      )
    }

  private def withRunningScheduler(
      test: StubScheduler[IO] => IO[Assertion]
  ): IO[Assertion] =
    StubScheduler[IO].use(test)

}
