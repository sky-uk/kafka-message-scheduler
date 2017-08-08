package com.sky.kms.avro

import java.time.OffsetDateTime

import com.sksamuel.avro4s._
import com.sky.kms.common.BaseSpec
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData

class AvroSpec extends BaseSpec {

  private case class TestData(time: OffsetDateTime)

  "avro" should {
    "create a schema for OffsetDateTime" in {
      val schema = AvroSchema[TestData]
      schema.getField("time").schema().getType shouldBe Schema.Type.STRING
    }

    "serialize an OffsetDateTime to an Avro String type" in {
      val timeString = "2017-07-18T16:04:54.059+01:00"
      val testData = TestData(OffsetDateTime.parse(timeString))

      val genericRecord = RecordFormat[TestData].to(testData)

      genericRecord.get("time") shouldBe timeString
    }

    "deserialize an OffsetDateTime from an Avro String type" in {
      val timeString = "2017-07-18T16:04:54.059+01:00"
      val genericRecord = new GenericData.Record(AvroSchema[TestData])
      genericRecord.put("time", timeString)

      val testData = RecordFormat[TestData].from(genericRecord)

      testData.time shouldBe OffsetDateTime.parse(timeString)
    }
  }
}
