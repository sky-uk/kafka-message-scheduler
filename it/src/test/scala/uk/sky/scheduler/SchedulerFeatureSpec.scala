package uk.sky.scheduler

import cats.effect.{Clock, IO, Resource}
import cats.syntax.all.*
import uk.sky.scheduler.domain.{Schedule, ScheduleWithoutHeaders}
import uk.sky.scheduler.kafka.json.JsonSchedule
import uk.sky.scheduler.syntax.all.*
import uk.sky.scheduler.util.KafkaUtil

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

import util.SchedulerFeatureBase

final class SchedulerFeatureSpec extends SchedulerFeatureBase {

  val timeout: FiniteDuration = config.timeout.seconds
  val kafkaBootstrapServer    = config.bootstrapServer
  val consumerGroup           = config.groupId

  override given executionContext: ExecutionContext = ExecutionContext.global
  override given patienceConfig: PatienceConfig     = PatienceConfig(timeout)
  override val ResourceTimeout: Duration            = timeout

  override val resource = Resource.pure(KafkaUtil[IO](kafkaBootstrapServer, timeout, consumerGroup))

  "scheduler" should {
    "schedule a Json event for the specified time" in { kafkaUtil =>
      val outputTopic     = "output-json-topic"
      val outputJsonKey   = "jsonKey"
      val outputJsonValue = "jsonValue"

      for {
        scheduledTime <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule       = createJsonSchedule(scheduledTime, outputTopic, outputJsonKey, outputJsonValue)
        _             <- kafkaUtil.produce[JsonSchedule]("json-schedules", "input-key-json" -> schedule.some)
        messages      <- kafkaUtil.consume[String](outputTopic, 1)
      } yield {
        val message = messages.loneElement
        message.keyValue shouldBe outputJsonKey -> outputJsonValue
      }
    }

    "schedule an Avro event for the specified time" in { kafkaUtil =>
      val outputTopic     = "output-avro-topic"
      val outputAvroKey   = "avroKey"
      val outputAvroValue = "avroValue"

      for {
        scheduledTime <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule       = createAvroSchedule(scheduledTime, outputTopic, outputAvroKey, outputAvroValue)
        _             <- kafkaUtil.produce[Schedule]("avro-schedules", "input-key-avro" -> schedule.some)
        messages      <- kafkaUtil.consume[String](outputTopic, 1)
      } yield {
        val message = messages.loneElement
        message.keyValue shouldBe outputAvroKey -> outputAvroValue
      }
    }

    "schedule an event immediately if it has schedule time in the past" in { kafkaUtil =>
      val outputTopic     = "output-topic"
      val outputJsonKey   = "someJsonKey"
      val outputJsonValue = "someJsonValue"

      for {
        now              <- IO.realTimeInstant
        pastScheduledTime = now.minusSeconds(600)
        schedule          = createJsonSchedule(pastScheduledTime.toEpochMilli, outputTopic, outputJsonKey, outputJsonValue)
        _                <- kafkaUtil.produce[JsonSchedule]("json-schedules", "input-key-json-past" -> schedule.some)
        messages         <- kafkaUtil.consume[String](outputTopic, 1)
      } yield {
        val message = messages.loneElement
        message.keyValue shouldBe outputJsonKey -> outputJsonValue
      }
    }

    "tombstone an input schedule when it is scheduled" in { kafkaUtil =>
      val scheduleKey     = "input-key-json-tombstone"
      val outputTopic     = "output-topic"
      val outputJsonKey   = "jsonKey"
      val outputJsonValue = "jsonValue"

      for {
        scheduledTime <- Clock[IO].epochMilli
        schedule       = createJsonSchedule(scheduledTime, outputTopic, outputJsonKey, outputJsonValue)
        _             <- kafkaUtil.produce[JsonSchedule]("json-schedules", scheduleKey -> schedule.some)
        _             <- IO.sleep(10.second)
        inputMessages <- kafkaUtil.consumeLast[Option[String]]("json-schedules", 2)
      } yield {
        val scheduled = inputMessages.headOption.value
        scheduled.value shouldBe defined

        val tombstone = inputMessages.lastOption.value
        tombstone.keyValue shouldBe scheduleKey -> None
        tombstone.headers.get("schedule:expired").value shouldBe "true"
      }
    }

    "process an avro schedule without headers" in { kafkaUtil =>
      val outputTopic     = "output-avro-topic"
      val outputAvroKey   = "avroKey"
      val outputAvroValue = "avroValue"

      for {
        scheduledTime <- Clock[IO].epochMilli(_.plusSeconds(5))
        schedule       = createAvroScheduleWithoutHeaders(scheduledTime, outputTopic, outputAvroKey, outputAvroValue)
        _             <- kafkaUtil.produce[ScheduleWithoutHeaders]("avro-schedules", "input-key-avro" -> schedule.some)
        messages      <- kafkaUtil.consume[String](outputTopic, 1)
      } yield {
        val message = messages.loneElement
        message.keyValue shouldBe outputAvroKey -> outputAvroValue
      }
    }

  }

}
