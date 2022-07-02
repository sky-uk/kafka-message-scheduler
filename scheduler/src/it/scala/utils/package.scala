import java.io.ByteArrayOutputStream
import java.time.{Duration, OffsetDateTime, ZoneOffset, ZonedDateTime}
import com.fortysevendeg.scalacheck.datetime.GenDateTime.genDateTimeWithinRange
import com.fortysevendeg.scalacheck.datetime.instances.jdk8._
import com.sksamuel.avro4s.{AvroOutputStream, Encoder}
import com.sky.kms.avro._
import com.sky.kms.domain.PublishableMessage.ScheduledMessage
import com.sky.kms.domain.Schedule.{ScheduleNoHeaders, ScheduleWithHeaders}
import com.sky.kms.domain.{Schedule, ScheduleEvent}
import org.scalacheck.{Arbitrary, Gen}

import scala.concurrent.duration._
import scala.util.Using

package object utils {
  implicit val arbAlphaString: Arbitrary[String] =
    Arbitrary(Gen.alphaStr.suchThat(_.nonEmpty).retryUntil(_.nonEmpty))

  implicit val arbNextMonthOffsetDateTime: Arbitrary[OffsetDateTime] = {
    val from  = ZonedDateTime.now()
    val range = Duration.ofDays(20)
    Arbitrary(genDateTimeWithinRange(from, range).map(_.withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime))
  }

  implicit val encoderForScheduleWithHeaders = Encoder[ScheduleWithHeaders]
  implicit val encoderForScheduleNoHeaders   = Encoder[ScheduleNoHeaders]

  implicit class ScheduleEventOps(private val schedule: ScheduleEvent) extends AnyVal {
    def toSchedule: ScheduleWithHeaders = {
      val time = OffsetDateTime.now().toInstant.plusMillis(schedule.delay.toMillis).atOffset(ZoneOffset.UTC)
      ScheduleWithHeaders(time, schedule.outputTopic, schedule.key, schedule.value, schedule.headers)
    }

    def secondsFromNow(seconds: Long): ScheduleEvent =
      schedule.copy(delay = seconds.seconds)

    def toScheduledMessage: ScheduledMessage =
      ScheduledMessage(schedule.inputTopic, schedule.outputTopic, schedule.key, schedule.value, schedule.headers)

    def headerKeys: Iterable[String]        = schedule.headers.keys
    def headerValues: Iterable[Array[Byte]] = schedule.headers.values
  }

  implicit class ScheduleEventNoHeadersOps(private val schedule: ScheduleEventNoHeaders) extends AnyVal {
    def toScheduleWithoutHeaders: ScheduleNoHeaders = {
      val time = OffsetDateTime.now().toInstant.plusMillis(schedule.delay.toMillis).atOffset(ZoneOffset.UTC)
      ScheduleNoHeaders(time, schedule.outputTopic, schedule.key, schedule.value)
    }

    def secondsFromNow(seconds: Long): ScheduleEventNoHeaders =
      schedule.copy(delay = seconds.seconds)

    def toScheduledMessage: ScheduledMessage =
      ScheduledMessage(schedule.inputTopic, schedule.outputTopic, schedule.key, schedule.value, Map.empty)
  }

  implicit class ScheduleOps[T <: Schedule](private val schedule: T) extends AnyVal {
    def toAvro(implicit e: Encoder[T]): Array[Byte] = toAvroFrom(schedule)
    def timeInMillis: Long                          = schedule.getTime.toInstant.toEpochMilli
  }

  private def toAvroFrom[T <: Schedule : Encoder](t: T): Array[Byte] =
    Using.resource(new ByteArrayOutputStream()) { baos =>
      val output = AvroOutputStream.binary[T].to(baos).build()
      output.write(t)
      output.close()
      baos.toByteArray
    }

}
