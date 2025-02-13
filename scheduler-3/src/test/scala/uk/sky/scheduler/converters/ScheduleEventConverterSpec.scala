package uk.sky.scheduler.converters

import cats.syntax.all.*
import fs2.kafka.{Header, Headers, ProducerRecord}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import uk.sky.scheduler.domain.{Metadata, Schedule, ScheduleEvent}
import uk.sky.scheduler.message.Metadata as MessageMetadata
import uk.sky.scheduler.util.Generator.given
import uk.sky.scheduler.util.ProducerRecordMatchers

import java.nio.charset.StandardCharsets

class ScheduleEventConverterSpec
  extends AnyWordSpec,
    Matchers,
    ScalaCheckPropertyChecks,
    ProducerRecordMatchers,
    ScheduleEventConverter {

  "toProducerRecord" should {
    "Convert a ScheduleEvent into a ProducerRecord" in forAll {
      (producerRecord: ProducerRecord[Array[Byte], Option[Array[Byte]]], metadata: Metadata) =>
        val headers = producerRecord.headers.toChain.toList.map(header => header.key -> header.value).toMap

        val schedule = Schedule(
          time = Long.MaxValue,
          topic = producerRecord.topic,
          key = producerRecord.key,
          value = producerRecord.value,
          headers = headers
        )

        val scheduleEvent = ScheduleEvent(metadata, schedule)

        scheduleEvent.toProducerRecord should equalProducerRecord(producerRecord)
    }
  }

  "toTombstone" should {
    "Convert a ScheduleEvent into a Tombstone" in forAll {
      (producerRecord: ProducerRecord[String, Option[Array[Byte]]], schedule: Schedule) =>
        val tombstone = ProducerRecord[Array[Byte], Option[Array[Byte]]](
          topic = producerRecord.topic,
          key = producerRecord.key.getBytes(StandardCharsets.UTF_8),
          value = none[Array[Byte]]
        )
          .withHeaders(Headers(Header(MessageMetadata.expiredKey.toString, MessageMetadata.expiredValue)))

        val metadata = Metadata(
          id = producerRecord.key,
          scheduleTopic = producerRecord.topic
        )

        val scheduleEvent = ScheduleEvent(metadata, schedule)

        scheduleEvent.toTombstone should equalProducerRecord(tombstone)
    }
  }

}