package uk.sky.scheduler.kafka.avro

import cats.syntax.all.*
import vulcan.Codec

given avroScheduleCodec: Codec[AvroSchedule] = Codec.record[AvroSchedule](
  name = "Schedule",
  namespace = "com.sky.kms.domain"
) { field =>
  (
    field("time", _.time),
    field("topic", _.topic),
    field("key", _.key),
    field("value", _.value),
    field("headers", _.headers)
  ).mapN(AvroSchedule.apply)
}
