package com.sky.kms.unit

import java.util.UUID

import akka.testkit.TestActorRef
import com.sky.kms.actors.PublisherActor
import com.sky.kms.actors.PublisherActor._
import com.sky.kms.base.AkkaSpecBase
import com.sky.kms.utils.TestDataUtils._
import com.sky.kms.domain.{ScheduleEvent, ScheduleId}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import akka.stream.scaladsl.Source
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Keep
import akka.stream.ActorMaterializer
import com.sky.kms.domain.PublishableMessage.ScheduledMessage

import scala.concurrent.Future
import scala.concurrent.duration._

class PublisherActorSpec extends AkkaSpecBase with MockitoSugar {

  "A publisher actor" must {

    "add scheduled messages to the queue" in new TestContext {
      initQueue
      val (scheduleId, schedule) = generateSchedule
      publisherActor ! Trigger(scheduleId, schedule)
      verify(mockSourceQueue).offer((scheduleId, schedule.toScheduledMessage))
    }

    "stop when offering to the queue fails because the buffer is full" in new TestContext {
      initQueue
      watch(publisherActor)
      val (scheduleId, schedule) = generateSchedule
      when(mockSourceQueue.offer((scheduleId, schedule.toScheduledMessage)))
        .thenReturn(Future.failed(new IllegalStateException("buffer full!")))

      publisherActor ! Trigger(scheduleId, schedule)

      expectTerminated(publisherActor)
    }

    "do not offer when queue backpressures" in new TestContext {

      val (_, schedule) = generateSchedule

      implicit val mat = ActorMaterializer()

      watch(publisherActor)
      val (sourceQueue, _) = Source
        .queue[(String, ScheduledMessage)](1, OverflowStrategy.backpressure)
        .throttle(50, 1.second)
        .toMat(Sink.queue())(Keep.both)
        .run()

      publisherActor ! Init(sourceQueue)
      (1 to 1500).map { id =>
        publisherActor ! Trigger(id.toString, schedule)
      }
    }

    "stop when offering to the queue fails" in new TestContext {
      initQueue
      watch(publisherActor)
      val (scheduleId, schedule) = generateSchedule
      when(mockSourceQueue.offer((scheduleId, schedule.toScheduledMessage)))
        .thenReturn(Future.failed(new Exception("boom!")))

      publisherActor ! Trigger(scheduleId, schedule)

      expectTerminated(publisherActor)
    }

    "stop when queue fails" in new TestContext {
      initQueue
      watch(publisherActor)

      publisherActor ! DownstreamFailure(new Exception("boom!"))

      expectTerminated(publisherActor)
    }

  }

  private class TestContext {

    val mockSourceQueue = mock[ScheduleQueue]

    val publisherActor = TestActorRef(new PublisherActor)

    def initQueue = {
      when(mockSourceQueue.watchCompletion()).thenReturn(Future.never)
      publisherActor ! Init(mockSourceQueue)
    }

    def generateSchedule: (ScheduleId, ScheduleEvent) =
      (UUID.randomUUID().toString, random[ScheduleEvent])
  }

}
