package com.sky.kafka.message.scheduler

import java.util.UUID

import akka.stream.testkit.scaladsl.TestSource
import akka.testkit.TestProbe
import com.sky.kafka.message.scheduler.SchedulingActor.{Ack, Init}
import com.sky.kafka.message.scheduler.domain.Schedule
import com.sky.kafka.message.scheduler.streams.ScheduleReader
import common.AkkaStreamBaseSpec
import common.TestDataUtils._

class ScheduleReaderSpec extends AkkaStreamBaseSpec {

  "toSchedulingMessage" should {

    "generate a CreateOrUpdate message if there is a schedule" in {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule])
      ScheduleReader.toSchedulingMessage(Right((scheduleId, Some(schedule)))) shouldBe
        SchedulingActor.CreateOrUpdate(scheduleId, schedule)
    }
  }

}
