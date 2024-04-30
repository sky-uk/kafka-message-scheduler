package uk.sky.scheduler.converters

import java.nio.charset.StandardCharsets
import java.util.Base64

import io.scalaland.chimney.Transformer

private trait Base64Converter {
  private val b64Decoder = Base64.getDecoder
  private val b64Encoder = Base64.getEncoder

  private[converters] given b64EncodeTransformer: Transformer[Array[Byte], String] =
    (src: Array[Byte]) => src.base64Encode

  private[converters] given b64DecodeTransformer: Transformer[String, Array[Byte]] =
    (src: String) => src.base64Decode

  extension (s: String) {
    def base64Decode: Array[Byte] = b64Decoder.decode(s.getBytes(StandardCharsets.UTF_8))

    def base64Encode: String = s.getBytes(StandardCharsets.UTF_8).base64Encode
  }

  extension (bytes: Array[Byte]) {
    def base64Encode: String = b64Encoder.encodeToString(bytes)
  }
}

object base64 extends Base64Converter
