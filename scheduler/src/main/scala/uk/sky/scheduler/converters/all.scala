package uk.sky.scheduler.converters

import io.scalaland.chimney.Transformer

object all extends Base64Converter, ScheduleEventConverter, ConsumerRecordConverter

import base64.*

private given b64EncodeTransformer: Transformer[Array[Byte], String] =
  (src: Array[Byte]) => src.base64Encode

private given b64DecodeTransformer: Transformer[String, Array[Byte]] =
  (src: String) => src.base64Decode
