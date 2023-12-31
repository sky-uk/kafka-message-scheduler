package uk.sky.scheduler.stubs

import org.scalatest.Assertions.fail
import uk.sky.scheduler.domain.ScheduleEvent

enum TestEvent {
  case Scheduled(event: ScheduleEvent)
  case Expired(event: ScheduleEvent)
  case Canceled(id: String)
}

object TestEvent {
  extension (event: TestEvent) {
    def scheduled: TestEvent.Scheduled = event match {
      case s: TestEvent.Scheduled => s
      case other                  => fail(s"TestEvent was not 'Scheduled' but was '$other'")
    }

    def expired: TestEvent.Expired = event match {
      case e: TestEvent.Expired => e
      case other                => fail(s"TestEvent was not 'Expired' but was '$other''")
    }

    def canceled: TestEvent.Canceled = event match {
      case c: TestEvent.Canceled => c
      case other                 => fail(s"TestEvent was not 'Canceled' but was '$other'")
    }
  }
}
