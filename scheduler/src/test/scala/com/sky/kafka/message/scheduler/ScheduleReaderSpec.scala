//package com.sky.kafka.message.scheduler
//
//import java.util.UUID
//
//import akka.testkit.TestProbe
//import com.sky.kafka.message.scheduler.domain.Schedule
//import common.{AkkaStreamBaseSpec, BaseSpec}
//import common.TestDataUtils._
//
//class ScheduleReaderSpec extends AkkaStreamBaseSpec {
//
//  "stream" should {
//    "send schedules to the scheduling actor" in {
//      val (scheduleId, schedule) = (UUID.randomUUID().toString, random[Schedule])
//      val probe = TestProbe()
//      val scheduleReader = ScheduleReader(source, probe.ref, system)
//
//      scheduleReader.stream.run()
//
//      probe.expectMsg(SchedulingActor.CreateOrUpdate(scheduleId, schedule))
//    }
//  }
//
//}
