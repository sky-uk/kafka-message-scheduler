import com.sksamuel.avro4s.{AvroOutputStream, Encoder}
import com.sky.kms.domain.Schedule
import com.sky.kms.domain.Schedule.{ScheduleNoHeaders, ScheduleWithHeaders}
import com.sky.kms.avro._

import java.io.ByteArrayOutputStream
import scala.util.Using

package object utils {
  implicit val encoderForScheduleWithHeaders = Encoder[ScheduleWithHeaders]
  implicit val encoderForScheduleNoHeaders   = Encoder[ScheduleNoHeaders]

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
