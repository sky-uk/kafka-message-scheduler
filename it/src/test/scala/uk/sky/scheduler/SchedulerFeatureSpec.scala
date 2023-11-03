package uk.sky.scheduler

import java.nio.charset.StandardCharsets

import cats.effect.testing.scalatest.{AsyncIOSpec, CatsResourceIO}
import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.kafka.ValueSerializer
import io.circe.syntax.*
import org.scalatest.LoneElement
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.FixtureAsyncWordSpec
import uk.sky.scheduler.circe.given
import uk.sky.scheduler.domain.Schedule
import uk.sky.scheduler.kafka.avro.{avroBinarySerializer, avroScheduleCodec, AvroSchedule}
import uk.sky.scheduler.syntax.all.*
import uk.sky.scheduler.util.KafkaUtil

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

/*
TODO - investigate why dockerComposeTest gives
Error occurred during initialization of VM
java.lang.Error: Properties init: Could not determine current working directory.
 */
final class SchedulerFeatureSpec
    extends FixtureAsyncWordSpec,
      AsyncIOSpec,
      CatsResourceIO[KafkaUtil[IO]],
      Matchers,
      Eventually,
      LoneElement {
  override given executionContext: ExecutionContext = ExecutionContext.global

  val timeout                                   = 15.seconds
  override given patienceConfig: PatienceConfig = PatienceConfig(timeout = timeout)

  override val resource: Resource[IO, KafkaUtil[IO]] = KafkaUtil.apply[IO](9094, timeout)

  def createJsonSchedule(time: Long, topic: String, key: String, value: String): IO[Schedule] =
    for {
      key   <- key.base64Encode[IO]
      value <- value.base64Encode[IO]
    } yield Schedule(
      time = time,
      topic = topic,
      key = key,
      value = value.some,
      headers = Map.empty
    )

  def createAvroSchedule(time: Long, topic: String, key: String, value: String): AvroSchedule =
    AvroSchedule(
      time = time,
      topic = topic,
      key = key.getBytes(StandardCharsets.UTF_8),
      value = value.getBytes(StandardCharsets.UTF_8).some,
      headers = Map.empty
    )

  given avroSerializer: ValueSerializer[IO, AvroSchedule] = avroBinarySerializer[IO, AvroSchedule]

  "scheduler" should {
    "schedule a JSON event for the specified time" in { kafkaUtil =>
      val outputTopic     = "output-topic"
      val outputJsonKey   = "jsonKey"
      val outputJsonValue = "jsonValue"

      for {
        scheduledTime <- IO.realTimeInstant.map(_.plusSeconds(5).toEpochMilli)
        schedule      <- createJsonSchedule(scheduledTime, outputTopic, outputJsonKey, outputJsonValue)
        _             <- kafkaUtil.produce[String]("json-schedules", "input-key-json" -> schedule.asJson.noSpaces.some)
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
        _             <- kafkaUtil.produce[AvroSchedule]("schedules", "input-key-avro" -> schedule.some)
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
        schedule         <- createJsonSchedule(pastScheduledTime.toEpochMilli, outputTopic, outputJsonKey, outputJsonValue)
        _                <- kafkaUtil.produce[String]("json-schedules", "input-key-json-past" -> schedule.asJson.noSpaces.some)
        messages         <- kafkaUtil.consume[String](outputTopic, 1)
      } yield {
        val message = messages.loneElement
        message.keyValue shouldBe (outputJsonKey -> outputJsonValue)
        message.producedAt.toEpochMilli shouldBe now.toEpochMilli +- 500L
      }
    }
  }

}
