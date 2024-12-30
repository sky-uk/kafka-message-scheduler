package uk.sky.scheduler.converters

import io.scalaland.chimney.{PartialTransformer, Transformer}
import uk.sky.scheduler.converters.base64.*

object all extends Base64Converter, ScheduleEventConverter, ConsumerRecordConverter

private given b64EncodeTransformer: Transformer[Array[Byte], String] =
  (src: Array[Byte]) => src.base64Encode

private given b64DecodeTransformer: PartialTransformer[String, Array[Byte]] =
  PartialTransformer.fromFunction(_.base64Decode)
