package com.sky.kms.unit

import java.util.UUID

import akka.stream.QueueOfferResult.Enqueued
import akka.testkit.TestActorRef
import com.sky.kms.actors.PublisherActor
import com.sky.kms.actors.PublisherActor._
import com.sky.kms.actors.SchedulingActor.DownstreamFailure
import com.sky.kms.base.AkkaBaseSpec
import com.sky.kms.common.TestDataUtils._
import com.sky.kms.domain.{Schedule, ScheduleId}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.Future

class PublisherActorSpec extends AkkaBaseSpec with MockitoSugar {

  "A publisher actor" must {

    "add scheduled messages to the queue" in new PublisherActorTest {
      val (scheduleId, schedule) = generateSchedule
      publisherActor ! Trigger(scheduleId, schedule)
      verify(mockSourceQueue).offer((scheduleId, schedule.toScheduledMessage))
    }

    "stop when offering to the queue fails" in new PublisherActorTest {
      watch(publisherActor)
      val (scheduleId, schedule) = generateSchedule
      when(mockSourceQueue.offer((scheduleId, schedule.toScheduledMessage)))
        .thenReturn(Future.failed(new Exception("boom!")))

      publisherActor ! Trigger(scheduleId, schedule)

      expectTerminated(publisherActor)
    }

    "stop when queue fails" in new PublisherActorTest {
      watch(publisherActor)

      publisherActor ! DownstreamFailure(new Exception("boom!"))

      expectTerminated(publisherActor)
    }

    "keep triggering a scheduled message if the queue buffer is full" in new PublisherActorTest {
      val (scheduleId, schedule) = generateSchedule
      when(mockSourceQueue.offer((scheduleId, schedule.toScheduledMessage)))
        .thenReturn(Future.failed(new IllegalStateException), Future.successful(Enqueued))

      publisherActor ! Trigger(scheduleId, schedule)

      verify(mockSourceQueue, times(2)).offer((scheduleId, schedule.toScheduledMessage))
    }

  }

  private class PublisherActorTest {
    val mockSourceQueue = mock[ScheduleQueue]

    val publisherActor = TestActorRef(new PublisherActor(mockSourceQueue))

    def generateSchedule: (ScheduleId, Schedule) =
      (UUID.randomUUID().toString, random[Schedule])
  }

}
