package uk.sky.scheduler.syntax

import java.nio.charset.StandardCharsets
import java.util.Base64

trait Base64Syntax {
  private val b64Decoder = Base64.getDecoder
  private val b64Encoder = Base64.getEncoder

  extension (s: String) {
    def base64Decode: Array[Byte] =
      b64Decoder.decode(s.getBytes(StandardCharsets.UTF_8))

    def base64Encode: String =
      s.getBytes(StandardCharsets.UTF_8).base64Encode
  }

  extension (bytes: Array[Byte]) {
    def base64Encode: String =
      b64Encoder.encodeToString(bytes)
  }
}

object base64 extends Base64Syntax
