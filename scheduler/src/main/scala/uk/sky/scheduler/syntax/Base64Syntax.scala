package uk.sky.scheduler.syntax

import java.nio.charset.StandardCharsets
import java.util.Base64

trait Base64Syntax {
  extension (s: String) {
    def base64Decode: Array[Byte] =
      Base64.getDecoder.decode(s.getBytes(StandardCharsets.UTF_8))

    def base64Encode: String =
      s.getBytes(StandardCharsets.UTF_8).base64Encode
  }

  extension (bytes: Array[Byte]) {
    def base64Encode: String =
      Base64.getEncoder.encodeToString(bytes)
  }
}

object base64 extends Base64Syntax
