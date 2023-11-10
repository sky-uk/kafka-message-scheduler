package uk.sky.scheduler

import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.FixtureAsyncWordSpec
import org.scalatest.{LoneElement, OptionValues}
import uk.sky.scheduler.kafka.avro.AvroSchedule
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.util.{KafkaUtil, ScheduleHelpers}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

final class SchedulerFeatureSpec
    extends FixtureAsyncWordSpec,
      AsyncIOSpec,
      CatsResourceIO[KafkaUtil[IO]],
      ScheduleHelpers,
      OptionValues,
      Matchers,
      Eventually,
      LoneElement {
  val timeout: FiniteDuration = 15.seconds

  override given executionContext: ExecutionContext = ExecutionContext.global

  override given patienceConfig: PatienceConfig = PatienceConfig(timeout = timeout)

  override val resource: Resource[IO, KafkaUtil[IO]] = Resource.pure(KafkaUtil[IO](9094, timeout))

  "scheduler" should {
    "schedule a JSON event for the specified time" in { kafkaUtil =>
      val outputTopic     = "output-topic"
      val outputJsonKey   = "jsonKey"
      val outputJsonValue = "jsonValue"

      for {
        scheduledTime <- IO.realTimeInstant.map(_.plusSeconds(5).toEpochMilli)
        schedule       = createJsonSchedule(scheduledTime, outputTopic, outputJsonKey, outputJsonValue)
        _             <- kafkaUtil.produce[JsonSchedule]("json-schedules", "input-key-json" -> schedule.some)
        messages      <- kafkaUtil.consume[String](outputTopic, 1)
      } yield {
        val message = messages.loneElement
        message.keyValue shouldBe (outputJsonKey -> outputJsonValue)
        message.producedAt.toEpochMilli shouldBe scheduledTime +- 100L
      }
    }

    "schedule an Avro event for the specified time" in { kafkaUtil =>
      val outputTopic     = "output-avro-topic"
      val outputAvroKey   = "avroKey"
      val outputAvroValue = "avroValue"

      for {
        scheduledTime <- IO.realTimeInstant.map(_.plusSeconds(5).toEpochMilli)
        schedule       = createAvroSchedule(scheduledTime, outputTopic, outputAvroKey, outputAvroValue)
        _             <- kafkaUtil.produce[AvroSchedule]("avro-schedules", "input-key-avro" -> schedule.some)
        messages      <- kafkaUtil.consume[String](outputTopic, 1)
      } yield {
        val message = messages.loneElement
        message.keyValue shouldBe (outputAvroKey -> outputAvroValue)
        message.producedAt.toEpochMilli shouldBe scheduledTime +- 100L
      }
    }

    "schedule an event immediately if it has past" in { kafkaUtil =>
      val outputTopic     = "output-topic"
      val outputJsonKey   = "jsonKey"
      val outputJsonValue = "jsonValue"

      for {
        now              <- IO.realTimeInstant
        pastScheduledTime = now.minusSeconds(100)
        schedule          = createJsonSchedule(pastScheduledTime.toEpochMilli, outputTopic, outputJsonKey, outputJsonValue)
        _                <- kafkaUtil.produce[JsonSchedule]("json-schedules", "input-key-json-past" -> schedule.some)
        messages         <- kafkaUtil.consume[String](outputTopic, 1)
      } yield {
        val message = messages.loneElement
        message.keyValue shouldBe (outputJsonKey -> outputJsonValue)
        message.producedAt.toEpochMilli shouldBe now.toEpochMilli +- 500L
      }
    }

    "tombstone an input schedule when it is scheduled" in { kafkaUtil =>
      val scheduleKey     = "input-key-json-tombstone"
      val outputTopic     = "output-topic"
      val outputJsonKey   = "jsonKey"
      val outputJsonValue = "jsonValue"

      for {

        scheduledTime <- IO.realTimeInstant.map(_.toEpochMilli)
        schedule       = createJsonSchedule(scheduledTime, outputTopic, outputJsonKey, outputJsonValue)
        _             <- kafkaUtil.produce[JsonSchedule]("json-schedules", scheduleKey -> schedule.some)
        inputMessages <- kafkaUtil.consumeLast[Option[String]]("json-schedules", 2)
      } yield {
        val scheduled = inputMessages.headOption.value
        scheduled.value shouldBe defined
        val tombstone = inputMessages.lastOption.value
        tombstone.keyValue shouldBe (scheduleKey -> None)
        tombstone.headers.get("schedule:expired").value shouldBe "true"
      }
    }
  }

}
