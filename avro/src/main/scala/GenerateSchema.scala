import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import com.sksamuel.avro4s.*
import com.sky.kms.avro.*
import com.sky.kms.domain.Schedule.ScheduleWithHeaders

object GenerateSchema extends App {

  val outputDir = Paths.get("avro/target/schemas")
  Files.createDirectories(outputDir)

  val schema = AvroSchema[ScheduleWithHeaders]

  val json = schema.toString(true)
  val path = outputDir.resolve(Paths.get("schedule.avsc"))
  println(s"Generated schema file: $path")
  Files.write(path, json.getBytes(StandardCharsets.UTF_8))

}
