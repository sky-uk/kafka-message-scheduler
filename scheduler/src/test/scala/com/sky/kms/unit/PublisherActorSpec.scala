package com.sky.kms.unit

import java.util.UUID

import akka.stream.QueueOfferResult
import akka.testkit.TestActorRef
import com.sky.kms.actors.PublisherActor
import com.sky.kms.actors.PublisherActor._
import com.sky.kms.base.AkkaSpecBase
import com.sky.kms.domain.{ScheduleEvent, ScheduleId}
import com.sky.kms.utils.MockGauge
import com.sky.kms.utils.TestDataUtils._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future

class PublisherActorSpec extends AkkaSpecBase with MockitoSugar {

  "A publisher actor" must {

    "add scheduled messages to the queue" in new TestContext {
      val (scheduleId, schedule) = generateSchedule
      publisherActor ! Trigger(scheduleId, schedule)
      verify(mockSourceQueue).offer((scheduleId, schedule.toScheduledMessage))
    }

    "stop when offering to the queue fails because the buffer is full" in new TestContext {
      watch(publisherActor)
      val (scheduleId, schedule) = generateSchedule
      when(mockSourceQueue.offer((scheduleId, schedule.toScheduledMessage)))
        .thenReturn(Future.failed(new IllegalStateException("buffer full!")))

      publisherActor ! Trigger(scheduleId, schedule)

      expectTerminated(publisherActor)
    }

    "stop when offering to the queue fails" in new TestContext {
      watch(publisherActor)
      val (scheduleId, schedule) = generateSchedule
      when(mockSourceQueue.offer((scheduleId, schedule.toScheduledMessage)))
        .thenReturn(Future.failed(new Exception("boom!")))

      publisherActor ! Trigger(scheduleId, schedule)

      expectTerminated(publisherActor)
    }

    "stop when queue fails" in new TestContext {
      watch(publisherActor)

      publisherActor ! DownstreamFailure(new Exception("boom!"))

      expectTerminated(publisherActor)
    }

    "decrement schedule counter when message is queued" in new TestContext {
      val (scheduleId, schedule) = generateSchedule

      when(mockSourceQueue.offer((scheduleId, schedule.toScheduledMessage)))
        .thenReturn(Future.successful(QueueOfferResult.Enqueued))

      publisherActor ! Trigger(scheduleId, schedule)

      eventually {
        mockGauge.counter.get() shouldBe -1L
      }
    }

    "decrement schedule counter when message is dropped" in new TestContext {
      val (scheduleId, schedule) = generateSchedule

      when(mockSourceQueue.offer((scheduleId, schedule.toScheduledMessage)))
        .thenReturn(Future.successful(QueueOfferResult.Dropped))

      publisherActor ! Trigger(scheduleId, schedule)

      eventually {
        mockGauge.counter.get() shouldBe -1L
      }
    }
  }

  private class TestContext {
    val mockSourceQueue = mock[ScheduleQueue]
    val mockGauge       = new MockGauge()
    val publisherActor  = TestActorRef(new PublisherActor(mockGauge))

    when(mockSourceQueue.watchCompletion()).thenReturn(Future.never)
    publisherActor ! Init(mockSourceQueue)

    def generateSchedule: (ScheduleId, ScheduleEvent) =
      (UUID.randomUUID().toString, random[ScheduleEvent])
  }

}
