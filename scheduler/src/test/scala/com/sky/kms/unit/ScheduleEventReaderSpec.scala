package com.sky.kms.unit

import java.util.UUID

import com.sky.kms.actors.SchedulingActor
import com.sky.kms.base.AkkaStreamBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain._
import com.sky.kms.streams.ScheduleReader

class ScheduleEventReaderSpec extends AkkaStreamBaseSpec {

  "toSchedulingMessage" should {

    "generate a CreateOrUpdate message if there is a schedule" in {
      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[ScheduleEvent])
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
