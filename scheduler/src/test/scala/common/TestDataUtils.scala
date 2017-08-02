package common

import java.io.ByteArrayOutputStream
import java.time.{Instant, OffsetDateTime, ZoneId}

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import com.sksamuel.avro4s.{AvroOutputStream, ToRecord}
import com.sky.kafka.message.scheduler.domain.Schedule
import org.scalacheck._
import com.sky.kafka.message.scheduler.avro._

object TestDataUtils extends RandomDataGenerator {

  implicit val arbAlphaString: Arbitrary[String] = Arbitrary(Gen.alphaStr.suchThat(!_.isEmpty))

  implicit val arbNextMonthOffsetDateTime: Arbitrary[OffsetDateTime] = {
    val low = OffsetDateTime.now()
    val high = low.plusMonths(1)
    Arbitrary(Gen.choose(low.toEpochSecond, high.toEpochSecond)
      .map(epoch => OffsetDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault())))
  }

  implicit class ScheduleOps(val schedule: Schedule) extends AnyVal {
    def toAvro(implicit toRecord: ToRecord[Schedule]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[Schedule](baos)
      output.write(schedule)
      output.close()
      baos.toByteArray
    }

    def timeInMillis: Long = schedule.time.toInstant.toEpochMilli
  }

}
