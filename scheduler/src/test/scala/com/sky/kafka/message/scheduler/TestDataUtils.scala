package com.sky.kafka.message.scheduler

import java.io.ByteArrayOutputStream
import java.time.OffsetDateTime

import com.danielasfregola.randomdatagenerator.RandomDataGenerator
import com.sksamuel.avro4s.{AvroOutputStream, ToRecord}
import com.sky.kafka.message.scheduler.domain.Schedule
import org.scalacheck._
import avro._

object TestDataUtils extends RandomDataGenerator {

  implicit val arbAlphaString: Arbitrary[String] = Arbitrary(Gen.alphaStr.suchThat(!_.isEmpty))

  //implicit val chooseOffsetDateTime: Choose[OffsetDateTime] = Choose.xmap[OffsetDateTime, OffsetDateTime](OffsetDateTime.MIN, OffsetDateTime.MAX)

  //TODO java.time.DateTimeException: Invalid value for Year (valid values -999999999 - 999999999): 1000000000
  implicit val arbOffsetDateTime: Arbitrary[OffsetDateTime] =
    Arbitrary(Gen.oneOf(OffsetDateTime.MIN, OffsetDateTime.MAX))

  implicit class ScheduleOps(val schedule: Schedule) extends AnyVal {
    def toAvro(implicit toRecord: ToRecord[Schedule]): Array[Byte] = {
      val baos = new ByteArrayOutputStream()
      val output = AvroOutputStream.binary[Schedule](baos)
      output.write(schedule)
      output.close()
      baos.toByteArray
    }
  }

}
