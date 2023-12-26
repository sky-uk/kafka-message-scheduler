package uk.sky.scheduler

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{Clock, IO}
import cats.syntax.all.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{Assertion, LoneElement}
import uk.sky.scheduler.Message.Headers
import uk.sky.scheduler.domain.{Metadata, Schedule, ScheduleEvent}
import uk.sky.scheduler.error.ScheduleError
import uk.sky.scheduler.stubs.StubScheduleQueue.Status
import uk.sky.scheduler.stubs.StubScheduler
import uk.sky.scheduler.util.testSyntax.*

final class SchedulerSpec extends AsyncWordSpec, AsyncIOSpec, Matchers, LoneElement {

  "Scheduler" should {
    "add a scheduled event to the queue" in withRunningScheduler { scheduler =>
      for {
        time                 <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule              = scheduleEvent("id", time = time)
        _                    <- scheduler.produce(message(schedule))
        resultPair           <- scheduler.consume(1).realTimeInstant
        (publishTime, result) = resultPair
      } yield {
        result.loneElement shouldBe schedule
        publishTime.toEpochMilli shouldBe time +- 50
      }
    }

    "schedule an event immediately if it has past" in withRunningScheduler { scheduler =>
      for {
        now                  <- Clock[IO].epochMilli
        schedule              = scheduleEvent("id", time = now - 100_000L)
        _                    <- scheduler.produce(message(schedule))
        resultPair           <- scheduler.consume(1).realTimeInstant
        (publishTime, result) = resultPair
      } yield {
        result.loneElement shouldBe schedule
        publishTime.toEpochMilli shouldBe now +- 50
      }
    }

    "cancel a schedule on a delete message" in withRunningScheduler { scheduler =>
      for {
        _       <- scheduler.backgroundStart
        time    <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule = scheduleEvent("id", time = time)
        _       <- scheduler.produce(message(schedule))
        _       <- scheduler.takeEvent.asserting(_ shouldBe (schedule.metadata.id -> Status.Scheduled))
        _       <- scheduler.produce(deleteMessage(schedule.metadata.id))
        result  <- scheduler.takeEvent
      } yield result shouldBe (schedule.metadata.id -> Status.Canceled)
    }

    "ignore a delete if it is an expiry" in withRunningScheduler { scheduler =>
      for {
        time       <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule    = scheduleEvent("id", time = time)
        _          <- scheduler.produce(message(schedule))
        _          <- scheduler.produce(deleteMessage(schedule.metadata.id, expire = true))
        resultPair <- scheduler.consume(1).realTimeInstant
        (_, result) = resultPair
      } yield result.loneElement shouldBe schedule
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

  private def message(
      scheduleEvent: ScheduleEvent
  ): Message[Either[ScheduleError, Option[ScheduleEvent]]] =
    Message(
      key = scheduleEvent.metadata.id,
      source = scheduleEvent.metadata.scheduleTopic,
      value = scheduleEvent.some.asRight[ScheduleError],
      headers = Headers.empty
    )

  private def deleteMessage(
      id: String,
      expire: Boolean = false
  ): Message[Either[ScheduleError, Option[ScheduleEvent]]] = {
    val message = Message(
      key = id,
      source = "source-topic",
      value = none[ScheduleEvent].asRight[ScheduleError],
      headers = Headers.empty
    )

    if (expire) message.expire else message
  }

  private def withRunningScheduler(
      test: StubScheduler[IO] => IO[Assertion]
  ): IO[Assertion] =
    StubScheduler[IO].use(test)

}
