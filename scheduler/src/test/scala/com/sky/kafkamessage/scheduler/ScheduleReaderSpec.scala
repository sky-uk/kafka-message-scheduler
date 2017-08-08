package com.sky.kafkamessage.scheduler

import java.util.UUID

import com.sky.kafkamessage.scheduler.domain._
import com.sky.kafkamessage.scheduler.streams.ScheduleReader
import common.AkkaStreamBaseSpec
import common.TestDataUtils._

class ScheduleReaderSpec extends AkkaStreamBaseSpec {

  "toSchedulingMessage" should {

    "generate a CreateOrUpdate message if there is a schedule" in {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule])
      ScheduleReader.toSchedulingMessage(Right((scheduleId, Some(schedule)))) shouldBe
        Right(SchedulingActor.CreateOrUpdate(scheduleId, schedule))
    }

    "generate a Cancel message if there is no schedule" in {
      val scheduleId = UUID.randomUUID().toString
      ScheduleReader.toSchedulingMessage(Right((scheduleId, None))) shouldBe
        Right(SchedulingActor.Cancel(scheduleId))
    }
  }

}
